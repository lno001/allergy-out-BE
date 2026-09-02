package com.allergyout.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.allergyout.global.security.JwtUtil;
import com.allergyout.member.model.dao.MemberMapper;
import com.allergyout.member.model.vo.Member;
import com.allergyout.recipe.model.dao.RecipeMapper;
import com.allergyout.recipe.model.dto.RecipeListItem;
import com.allergyout.recipe.model.vo.Material;
import com.allergyout.recipe.model.vo.RecipeStep;
import com.allergyout.s3.S3Service;

/**
 * 실제 Tomcat을 랜덤 포트에 띄우고 진짜 HTTP multipart 요청을 보낸다. 인증도 실제 JwtFilter를 탄다.
 * 외부 의존(S3·Oracle)만 목으로 대체 — 자격증명이 없고 실 인프라에 쓰기가 나가면 안 되므로.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "DB_USERNAME=test", "DB_PASSWORD=test",
        "S3ACESSKEY=test", "S3SECRETKEY=test"
})
class RecipeApiServerTest {

    @LocalServerPort int port;

    @MockitoBean S3Service s3Service;
    @MockitoBean RecipeMapper recipeMapper;
    @MockitoBean MemberMapper memberMapper;      // UserDetailsServiceImpl 이 사용
    @MockitoBean DataSource dataSource;          // 실제 Oracle 연결 회피

    @Autowired JwtUtil jwtUtil;

    private RestClient client;
    private String bearer;   // "Bearer <jwt>" — accessToken 은 헤더로 전달 (refreshToken 만 쿠키)

    @BeforeEach
    void setUp() throws Exception {
        client = RestClient.create("http://localhost:" + port);

        Connection con = Mockito.mock(Connection.class, Mockito.RETURNS_DEFAULTS);
        lenient().when(dataSource.getConnection()).thenReturn(con);
        lenient().when(con.getAutoCommit()).thenReturn(true);

        // 로그인 회원: memberNo=42, memberId=tester
        Member member = Member.builder()
                .memberNo(42L).memberId("tester").memberPwd("x")
                .memberName("테스터").role("ROLE_USER").delYn("N").build();
        lenient().when(memberMapper.findByMemberId("tester")).thenReturn(Optional.of(member));

        bearer = "Bearer " + jwtUtil.createAccessToken("tester", 42L, "ROLE_USER");
    }

    private ByteArrayResource file(String filename) {
        return new ByteArrayResource(new byte[] { 1, 2, 3 }) {
            @Override public String getFilename() { return filename; }
        };
    }

    private MultipartBodyBuilder body(boolean withStep0Img) {
        MultipartBodyBuilder b = new MultipartBodyBuilder();
        b.part("recipeTitle", "된장국");
        b.part("recipeInfo", "나트륨을 줄인 된장국");
        b.part("recipeMainImg", file("main.jpg")).contentType(MediaType.IMAGE_JPEG);
        b.part("materialList[0].materialName", "두부");
        b.part("materialList[0].amount", "20g(2×2×2cm)");
        b.part("materialList[1].materialName", "감자");
        b.part("materialList[1].amount", "10g");
        b.part("stepList[0].stepOrder", "1");
        b.part("stepList[0].stepInfo", "감자, 양파는 얇게 썬다");
        if (withStep0Img) {
            b.part("stepList[0].stepImg", file("s0.jpg")).contentType(MediaType.IMAGE_JPEG);
        }
        b.part("stepList[1].stepOrder", "2");
        b.part("stepList[1].stepInfo", "냄비에 넣고 끓인다");
        return b;
    }

    // 해피패스 end-to-end: 실제 톰캣 + 실제 HTTP multipart + 실제 JwtFilter 인증을 통과해
    // 컨트롤러→서비스까지 도달하고, 서비스가 매퍼에 넘기는 값(인증 memberNo, 재료 2건, 스텝 이미지 URL/원본명)이
    // 요청 그대로인지 확인. 단위 테스트가 못 잡는 "프레임워크 배선"까지 검증.
    @Test
    @DisplayName("실서버 multipart POST /api/recipes → 201, 인증 memberNo·중첩 리스트·스텝 파일 전부 정상 바인딩")
    void createRecipe_realServer() {
        when(s3Service.upload(any(), anyString(), anyLong()))
                .thenReturn("https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/42/x.jpg");
        doAnswer(inv -> {
            Map<String, Object> p = inv.getArgument(0);
            p.put("recipeNo", 555L);
            return null;
        }).when(recipeMapper).insertRecipe(any());

        var res = client.post().uri("/api/recipes")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body(true).build())
                .retrieve()
                .toEntity(String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).contains("\"code\":201").contains("레시피 등록 성공했습니다.");

        ArgumentCaptor<Map<String, Object>> recipeCap = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(recipeMapper).insertRecipe(recipeCap.capture());
        assertThat(recipeCap.getValue())
                .containsEntry("memberNo", 42L)                 // ← 인증 principal에서 온 값
                .containsEntry("recipeTitle", "된장국")
                .containsEntry("recipesImgPath", "main.jpg");

        ArgumentCaptor<Material> matCap = ArgumentCaptor.forClass(Material.class);
        Mockito.verify(recipeMapper, Mockito.times(2)).insertMaterial(matCap.capture());
        assertThat(matCap.getAllValues()).extracting(Material::getMaterialName).containsExactly("두부", "감자");
        assertThat(matCap.getAllValues().get(0).getRecipeNo()).isEqualTo(555L);

        ArgumentCaptor<RecipeStep> stepCap = ArgumentCaptor.forClass(RecipeStep.class);
        Mockito.verify(recipeMapper, Mockito.times(2)).insertRecipeStep(stepCap.capture());
        assertThat(stepCap.getAllValues().get(0).getStepImgPath()).isEqualTo("s0.jpg");
        assertThat(stepCap.getAllValues().get(0).getStepImg()).isNotNull();
        assertThat(stepCap.getAllValues().get(1).getStepImg()).isNull();
        assertThat(stepCap.getAllValues().get(1).getStepImgPath()).isNull();
    }

    // 인가: Authorization 헤더 없이 오면 SecurityConfig 의 .authenticated() 규칙에 걸려
    // 컨트롤러/서비스에 도달하지 못하고 4xx(401)로 끊기는지. 비로그인 등록 차단.
    @Test
    @DisplayName("실서버: 토큰 없이 요청 → 401/403, 서비스 미호출")
    void createRecipe_noToken() {
        RestClientResponseException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                RestClientResponseException.class,
                () -> client.post().uri("/api/recipes")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(body(false).build())
                        .retrieve().toBodilessEntity());

        System.out.println(">>> noToken status = " + thrown.getStatusCode() + " body = " + thrown.getResponseBodyAsString());
        assertThat(thrown.getStatusCode().is4xxClientError()).isTrue();
        Mockito.verifyNoInteractions(s3Service);
    }

    // 보상 삭제 end-to-end: RECIPES INSERT 뒤 insertMaterial 이 터지면 @Transactional 이 DB 를 롤백하고,
    // catch 의 deleteQuietly 가 이미 올린 대표 이미지를 S3 에서 지우는지 (응답은 500).
    @Test
    @DisplayName("실서버: 중간 실패 시 업로드된 S3 파일 보상 삭제")
    void createRecipe_midFailure_compensates() {
        when(s3Service.upload(any(), anyString(), anyLong()))
                .thenReturn("https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/42/main.jpg");
        doAnswer(inv -> {
            Map<String, Object> p = inv.getArgument(0);
            p.put("recipeNo", 555L);
            return null;
        }).when(recipeMapper).insertRecipe(any());
        doThrow(new RuntimeException("DB 실패")).when(recipeMapper).insertMaterial(any());

        try {
            client.post().uri("/api/recipes")
                    .header(HttpHeaders.AUTHORIZATION, bearer)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body(false).build())
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(500);
        }

        Mockito.verify(s3Service).delete("recipes/42/main.jpg");
    }

    // 목록 조회 end-to-end: GET /api/recipes → 200, data 에 recipes[] + pageInfo(offset·totalPages 포함),
    // createDate 는 "yyyy-MM-dd" 로 직렬화. 인증 없이도 됨.
    @Test
    @DisplayName("실서버 GET /api/recipes → 200, recipes + pageInfo, createDate 포맷")
    void getRecipeList_realServer() {
        when(recipeMapper.getRecipeList(0, 20)).thenReturn(List.of(
                new RecipeListItem(101L, "된장국", "https://img/101.jpg", "관리자", LocalDate.of(2026, 8, 21))));
        when(recipeMapper.countRecipeList()).thenReturn(37);

        String body = client.get().uri("/api/recipes")
                .retrieve()
                .body(String.class);

        assertThat(body)
                .contains("\"code\":200")
                .contains("레시피 목록 조회 성공했습니다.")
                .contains("\"recipeNo\":101")
                .contains("\"memberName\":\"관리자\"")
                .contains("\"createDate\":\"2026-08-21\"")
                .contains("\"totalElements\":37")
                .contains("\"totalPages\":2")
                .contains("\"offset\":0");
    }
}
