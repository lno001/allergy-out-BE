package com.allergyout.recipe.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.allergyout.global.security.CustomUserDetails;
import com.allergyout.global.security.JwtFilter;
import com.allergyout.member.model.vo.Member;
import com.allergyout.recipe.model.dto.RecipeCreateRequest;
import com.allergyout.recipe.model.service.RecipeService;

/**
 * RecipeController 슬라이스 테스트 (@WebMvcTest).
 * 실제 Spring MVC 바인딩 파이프라인(@ModelAttribute + 중첩 리스트/파일)을 태워서,
 * multipart 폼 키가 DTO 로 제대로 조립되는지만 검증한다. 서비스는 목.
 */
@WebMvcTest(controllers = RecipeController.class)
@AutoConfigureMockMvc(addFilters = false) // 시큐리티 필터 우회 — 바인딩만 검증
class RecipeControllerBindingTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean RecipeService recipeService;
    @MockitoBean JwtFilter jwtFilter;

    @BeforeEach
    void auth() {
        CustomUserDetails user = new CustomUserDetails(Member.builder()
                .memberNo(1L).memberId("tester").memberPwd("x")
                .memberName("테스터").role("ROLE_USER").delYn("N").build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockMultipartFile file(String name) {
        return new MockMultipartFile(name, "photo.jpg", "image/jpeg", new byte[] { 1, 2, 3 });
    }

    // 핵심 검증: 평면 폼 키 materialList[0].materialName / stepList[0].stepImg(파일) 등이
    // 중첩 record 리스트로 바인딩되고, @AuthenticationPrincipal 의 memberNo 가 서비스로 전달되는지.
    // (이게 깨지면 등록 요청이 400 나거나 엉뚱한 데이터가 들어감)
    @Test
    @DisplayName("multipart form-data → @ModelAttribute 중첩 리스트/파일 바인딩 (camelCase 폼 키)")
    void createRecipe_multipartBinding() throws Exception {
        mockMvc.perform(multipart("/api/recipes")
                        .file(file("recipeMainImg"))
                        .file(file("stepList[0].stepImg"))
                        .param("recipeTitle", "된장국")
                        .param("recipeInfo", "나트륨을 줄인 된장국")
                        .param("materialList[0].materialName", "두부")
                        .param("materialList[0].amount", "20g")
                        .param("materialList[1].materialName", "감자")
                        .param("materialList[1].amount", "10g")
                        .param("stepList[0].stepOrder", "1")
                        .param("stepList[0].stepInfo", "감자, 양파는 얇게 썬다")
                        .param("stepList[1].stepOrder", "2")
                        .param("stepList[1].stepInfo", "냄비에 넣고 끓인다"))
                .andDo(print())
                .andExpect(status().isCreated());

        ArgumentCaptor<RecipeCreateRequest> req = ArgumentCaptor.forClass(RecipeCreateRequest.class);
        verify(recipeService).createRecipe(req.capture(), any(), eq(1L));

        RecipeCreateRequest r = req.getValue();
        assertThat(r.recipeTitle()).isEqualTo("된장국");
        assertThat(r.materialList()).hasSize(2);
        assertThat(r.materialList().get(0).materialName()).isEqualTo("두부");
        assertThat(r.materialList().get(1).materialName()).isEqualTo("감자");
        assertThat(r.stepList()).hasSize(2);
        assertThat(r.stepList().get(0).stepInfo()).isEqualTo("감자, 양파는 얇게 썬다");
        assertThat(r.stepList().get(0).stepImg()).isNotNull();
        assertThat(r.stepList().get(1).stepImg()).isNull();
    }
}
