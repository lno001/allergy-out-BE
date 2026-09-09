package com.allergyout.recipe.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;
import com.allergyout.recipe.model.dao.RecipeMapper;
import com.allergyout.recipe.model.dto.MaterialCreateRequest;
import com.allergyout.recipe.model.dto.MaterialUpdateRequest;
import com.allergyout.recipe.model.dto.RecipeCreateRequest;
import com.allergyout.recipe.model.dto.RecipeDetailItem;
import com.allergyout.recipe.model.dto.RecipeDetailResponse;
import com.allergyout.recipe.model.dto.RecipeListItem;
import com.allergyout.recipe.model.dto.RecipeListQuery;
import com.allergyout.recipe.model.dto.RecipeListResponse;
import com.allergyout.recipe.model.dto.RecipeRecommendResponse;
import com.allergyout.recipe.model.dto.RecipeUpdateRequest;
import com.allergyout.recipe.model.dto.StepCreateRequest;
import com.allergyout.recipe.model.dto.StepUpdateRequest;
import com.allergyout.recipe.model.vo.Material;
import com.allergyout.recipe.model.vo.Recipe;
import com.allergyout.recipe.model.vo.RecipeStep;
import com.allergyout.s3.S3Service;

/**
 * RecipeService.createRecipe 단위 테스트 (Mockito).
 * DAO·S3 는 목이라 실제 DB/S3 없이 서비스 로직(검증·오케스트레이션·보상삭제)만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock RecipeMapper recipeMapper;
    @Mock S3Service s3Service;
    @InjectMocks RecipeService recipeService;

    private static final Long MEMBER_NO = 7L;
    private static final String MAIN_URL = "https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/7/main.jpg";
    private static final String STEP_URL = "https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/steps/100/s1.jpg";

    private MockMultipartFile img(String field) {
        return new MockMultipartFile(field, "photo.jpg", "image/jpeg", new byte[] { 1, 2, 3 });
    }

    private StepCreateRequest step(int order, String info, MultipartFile stepImg) {
        return new StepCreateRequest(order, info, stepImg);
    }

    private RecipeCreateRequest request(List<StepCreateRequest> steps) {
        return new RecipeCreateRequest(
                "된장국",
                "나트륨을 줄인 된장국",
                "끓이기", "국&찌개",                  // cookingMethod, recipeType (6값)
                120.5, 10.0, 8.0, 3.0, 400.0,       // calorie, carbohydrate, protein, fat, sodium
                "두부",                              // mainMaterial
                List.of(new MaterialCreateRequest("두부", "20g")),
                steps);
    }

    /** insertRecipe(Map) 이 useGeneratedKeys 로 recipeNo 를 채워주는 동작을 흉내낸다. */
    private void stubRecipeInsertReturnsKey(long recipeNo) {
        doAnswer(inv -> {
            Map<String, Object> p = inv.getArgument(0);
            p.put("recipeNo", recipeNo);
            return null;
        }).when(recipeMapper).insertRecipe(any());
    }

    // 정상 흐름: RECIPES → MATERIAL(재료 수만큼) → RECIPE_STEPS(스텝 수만큼) 순서로
    // 매퍼가 올바른 횟수 호출되는지. 오케스트레이션 로직 회귀 방지.
    @Test
    @DisplayName("정상 등록: RECIPES 1회 · MATERIAL 1회 · RECIPE_STEPS 2회 매퍼 호출")
    void createRecipe_success() {
        when(s3Service.upload(any(), anyString(), anyLong())).thenReturn(MAIN_URL);
        stubRecipeInsertReturnsKey(100L);

        recipeService.createRecipe(
                request(List.of(step(1, "재료를 썬다", null), step(2, "끓인다", null))),
                img("recipeMainImg"), MEMBER_NO);

        verify(recipeMapper).insertRecipe(any());
        verify(recipeMapper, times(1)).insertMaterial(any());
        verify(recipeMapper, times(2)).insertRecipeStep(any());
    }

    // 대표 이미지 필수 규칙: validateCreateRequest 가 S3 업로드·INSERT 전에 예외로 끊는지.
    // (recipeMainImg 는 NOT NULL 컬럼이라 누락 시 조기 차단해야 함)
    @Test
    @DisplayName("대표 이미지 없으면 CustomException, 매퍼 미호출")
    void createRecipe_noMainImg() {
        assertThatThrownBy(() -> recipeService.createRecipe(
                request(List.of(step(1, "...", null))), null, MEMBER_NO))
                .isInstanceOf(CustomException.class);

        verify(recipeMapper, never()).insertRecipe(any());
    }

    // STEP_ORDER 중복 사전 차단: UK_RECIPE_STEPS_ORDER(RECIPE_NO, STEP_ORDER) 위반이
    // DB까지 가지 않도록 Service 에서 미리 걸러내는지. 걸리면 아무것도 INSERT 안 함.
    @Test
    @DisplayName("STEP_ORDER 중복이면 CustomException, 매퍼 미호출")
    void createRecipe_duplicateStepOrder() {
        assertThatThrownBy(() -> recipeService.createRecipe(
                request(List.of(step(1, "a", null), step(1, "b", null))),
                img("recipeMainImg"), MEMBER_NO))
                .isInstanceOf(CustomException.class);

        verify(recipeMapper, never()).insertRecipe(any());
    }

    // 스텝 이미지는 선택: 안 보낸 스텝은 STEP_IMG·STEP_IMG_PATH 가 null 로 저장되고
    // S3 업로드도 대표 이미지 1번만 일어나는지 (스텝 때문에 불필요한 업로드가 없어야 함).
    @Test
    @DisplayName("스텝 이미지 없으면 STEP_IMG/STEP_IMG_PATH는 null, S3 업로드는 대표 이미지 1회")
    void createRecipe_noStepImg_savesNull() {
        when(s3Service.upload(any(), anyString(), anyLong())).thenReturn(MAIN_URL);
        stubRecipeInsertReturnsKey(100L);

        recipeService.createRecipe(
                request(List.of(step(1, "재료를 썬다", null))),
                img("recipeMainImg"), MEMBER_NO);

        ArgumentCaptor<RecipeStep> captor = ArgumentCaptor.forClass(RecipeStep.class);
        verify(recipeMapper).insertRecipeStep(captor.capture());
        assertThat(captor.getValue().getStepImg()).isNull();
        assertThat(captor.getValue().getStepImgPath()).isNull();
        verify(s3Service, times(1)).upload(any(), anyString(), anyLong());
    }

    // 스텝 이미지가 있으면: recipes/steps 디렉터리 + recipeNo 키로 업로드하고,
    // 원본 파일명은 STEP_IMG 에, 리턴된 S3 URL 은 STEP_IMG_PATH 에 담기는지.
    @Test
    @DisplayName("스텝 이미지 있으면 recipes/steps 디렉터리로 업로드하고 STEP_IMG=원본명·STEP_IMG_PATH=URL 저장")
    void createRecipe_withStepImg_uploadsAndSavesUrl() {
        when(s3Service.upload(any(), eq("recipes"), anyLong())).thenReturn(MAIN_URL);
        when(s3Service.upload(any(), eq("recipes/steps"), anyLong())).thenReturn(STEP_URL);
        stubRecipeInsertReturnsKey(100L);

        recipeService.createRecipe(
                request(List.of(step(1, "재료를 썬다", img("stepList[0].stepImg")))),
                img("recipeMainImg"), MEMBER_NO);

        ArgumentCaptor<RecipeStep> captor = ArgumentCaptor.forClass(RecipeStep.class);
        verify(recipeMapper).insertRecipeStep(captor.capture());
        assertThat(captor.getValue().getStepImg()).isEqualTo("photo.jpg");   // STEP_IMG = 원본 파일명
        assertThat(captor.getValue().getStepImgPath()).isEqualTo(STEP_URL);  // STEP_IMG_PATH = S3 URL
        verify(s3Service).upload(any(), eq("recipes/steps"), eq(100L));
    }

    // 트랜잭션 밖 리소스(S3) 정합성: 중간(insertMaterial)에서 터지면
    // catch 가 이미 올린 S3 파일을 보상 삭제하고, 원래 예외는 그대로 위로 전파하는지.
    @Test
    @DisplayName("중간에 매퍼가 실패하면 업로드된 S3 파일을 보상 삭제하고 예외 전파")
    void createRecipe_midFailure_compensatesS3() {
        when(s3Service.upload(any(), anyString(), anyLong())).thenReturn(MAIN_URL);
        stubRecipeInsertReturnsKey(100L);
        doThrow(new RuntimeException("DB 실패")).when(recipeMapper).insertMaterial(any());

        assertThatThrownBy(() -> recipeService.createRecipe(
                request(List.of(step(1, "...", null))),
                img("recipeMainImg"), MEMBER_NO))
                .isInstanceOf(RuntimeException.class);

        verify(s3Service).delete("recipes/7/main.jpg");
    }

    // ---- 목록 조회 (GET /api/recipes) : 목록·검색·필터·정렬 통합 ----
    //  형식 검증(page·size 범위, recipeType/cookingMethod enum, applyMyAllergy 값)은 RecipeListQuery @Valid 담당 —
    //  Mockito 단위 테스트는 @Valid 를 안 태우므로 여기선 "정규화 결과가 매퍼로 어떻게 넘어가는지" 만 본다.

    // 편의: page/size 만 신경 쓰고 나머지 조건 없는 쿼리
    private RecipeListQuery baseQuery(Integer page, Integer size) {
        return new RecipeListQuery(page, size, null, null, null, null, null, null);
    }

    //  매퍼 getRecipeList 파라미터 순서 : offset, size, allergyMemberNo, bookmarkMemberNo, keyword,
    //  excludeMaterials, recipeType, cookingMethod, sort.
    //   - allergyMemberNo  : 알러지 제외용. applyMyAllergy=false 또는 비회원이면 null
    //   - bookmarkMemberNo : 즐겨찾기 표시용. 로그인 회원이면 항상 그 회원 PK (알러지 on/off 와 무관), 비회원이면 null

    // 조건 없음 + 비회원 : 매퍼에 전부 null, sort 는 기본 "latest", offset·pageInfo 계산
    @Test
    @DisplayName("목록(비회원, 조건 없음): 매퍼에 null·latest 전달 + offset·totalPages 계산")
    void getRecipeList_guestNoConditions() {
        List<RecipeListItem> rows = List.of(
                new RecipeListItem(2L, "김치찌개", "kimchi.jpg", "https://img/2.jpg", "관리자",
                        LocalDate.of(2026, 8, 21), "국&찌개", "끓이기", 210.0, "김치", 9L, false),
                new RecipeListItem(1L, "된장국", "doenjang.jpg", "https://img/1.jpg", "관리자",
                        LocalDate.of(2026, 8, 20), "국&찌개", "끓이기", 120.0, "두부", 3L, false));
        when(recipeMapper.getRecipeList(20, 10, null, null, null, null, null, null, "latest")).thenReturn(rows);
        when(recipeMapper.countRecipeList(null, null, null, null, null)).thenReturn(37);

        RecipeListResponse res = recipeService.getRecipeList(baseQuery(2, 10), null); // page 2 * size 10 = offset 20

        assertThat(res.recipes()).hasSize(2);
        assertThat(res.recipes().get(0).viewCount()).isEqualTo(9L);
        assertThat(res.pageInfo().getOffset()).isEqualTo(20);
        assertThat(res.pageInfo().getTotalElements()).isEqualTo(37);
        assertThat(res.pageInfo().getTotalPages()).isEqualTo(4); // ceil(37/10)
    }

    // 회원 + applyMyAllergy 미전송(null=true) : 알러지 제외 적용 → 알러지·즐겨찾기 둘 다 memberNo 전달
    @Test
    @DisplayName("목록(회원, applyMyAllergy 미전송): 알러지·즐겨찾기 둘 다 memberNo 전달")
    void getRecipeList_memberAppliesAllergyByDefault() {
        when(recipeMapper.getRecipeList(0, 20, 7L, 7L, null, null, null, null, "latest")).thenReturn(List.of());
        when(recipeMapper.countRecipeList(7L, null, null, null, null)).thenReturn(0);

        recipeService.getRecipeList(baseQuery(null, null), 7L);

        verify(recipeMapper).getRecipeList(0, 20, 7L, 7L, null, null, null, null, "latest");
        verify(recipeMapper).countRecipeList(7L, null, null, null, null);
    }

    // 회원 + applyMyAllergy="false" : 알러지 제외만 끔(allergyMemberNo=null) — 즐겨찾기 표시는 계속 동작(bookmarkMemberNo=7L)
    @Test
    @DisplayName("목록(회원, applyMyAllergy=false): allergyMemberNo=null 이어도 bookmarkMemberNo 는 유지")
    void getRecipeList_memberOptsOutAllergy() {
        when(recipeMapper.getRecipeList(0, 20, null, 7L, null, null, null, null, "latest")).thenReturn(List.of());
        when(recipeMapper.countRecipeList(null, null, null, null, null)).thenReturn(0);

        RecipeListQuery query = new RecipeListQuery(null, null, null, null, null, null, null, "false");
        recipeService.getRecipeList(query, 7L);

        verify(recipeMapper).getRecipeList(0, 20, null, 7L, null, null, null, null, "latest");
    }

    // keyword : 양쪽 공백 trim + LIKE 메타문자(\ % _) 이스케이프해서 매퍼로 (쿼리는 ESCAPE '\')
    @Test
    @DisplayName("목록: keyword 는 trim + %·_·\\ 이스케이프해서 매퍼에 전달")
    void getRecipeList_keywordNormalizedAndEscaped() {
        when(recipeMapper.getRecipeList(0, 20, null, null, "50\\% \\_ \\\\", null, null, null, "latest")).thenReturn(List.of());
        when(recipeMapper.countRecipeList(null, "50\\% \\_ \\\\", null, null, null)).thenReturn(0);

        RecipeListQuery query = new RecipeListQuery(null, null, "  50% _ \\  ", null, null, null, null, null);
        recipeService.getRecipeList(query, null);

        verify(recipeMapper).getRecipeList(0, 20, null, null, "50\\% \\_ \\\\", null, null, null, "latest");
    }

    // 공백뿐인 keyword → null (전체 조회)
    @Test
    @DisplayName("목록: 공백뿐인 keyword 는 null 로 전달")
    void getRecipeList_blankKeywordBecomesNull() {
        when(recipeMapper.getRecipeList(0, 20, null, null, null, null, null, null, "latest")).thenReturn(List.of());
        when(recipeMapper.countRecipeList(null, null, null, null, null)).thenReturn(0);

        RecipeListQuery query = new RecipeListQuery(null, null, "   ", null, null, null, null, null);
        recipeService.getRecipeList(query, null);

        verify(recipeMapper).getRecipeList(0, 20, null, null, null, null, null, null, "latest");
    }

    // excludeMaterials : null/blank 항목 제거 + 각 항목 trim + 이스케이프. 다 비면 null
    @Test
    @DisplayName("목록: excludeMaterials 는 blank 제거 + trim + 이스케이프 후 매퍼로")
    void getRecipeList_excludeMaterialsCleaned() {
        when(recipeMapper.getRecipeList(0, 20, null, null, null, List.of("계란", "우유\\%"), null, null, "latest")).thenReturn(List.of());
        when(recipeMapper.countRecipeList(null, null, List.of("계란", "우유\\%"), null, null)).thenReturn(0);

        RecipeListQuery query = new RecipeListQuery(null, null, null,
                java.util.Arrays.asList(" 계란 ", "", "  ", "우유%"), null, null, null, null);
        recipeService.getRecipeList(query, null);

        verify(recipeMapper).getRecipeList(0, 20, null, null, null, List.of("계란", "우유\\%"), null, null, "latest");
    }

    // excludeMaterials 가 blank 뿐이면 null 로
    @Test
    @DisplayName("목록: excludeMaterials 가 전부 blank 면 null 로 전달")
    void getRecipeList_excludeMaterialsAllBlankBecomesNull() {
        when(recipeMapper.getRecipeList(0, 20, null, null, null, null, null, null, "latest")).thenReturn(List.of());
        when(recipeMapper.countRecipeList(null, null, null, null, null)).thenReturn(0);

        RecipeListQuery query = new RecipeListQuery(null, null, null,
                java.util.Arrays.asList("  ", ""), null, null, null, null);
        recipeService.getRecipeList(query, null);

        verify(recipeMapper).getRecipeList(0, 20, null, null, null, null, null, null, "latest");
    }

    // recipeType·cookingMethod : trim 후 그대로 매퍼로 (6값 검증은 @Valid 담당). blank → null
    @Test
    @DisplayName("목록: recipeType·cookingMethod 는 trim 해서 매퍼로, blank 는 null")
    void getRecipeList_typeAndMethodPassthrough() {
        when(recipeMapper.getRecipeList(0, 20, null, null, null, null, "반찬", null, "latest")).thenReturn(List.of());
        when(recipeMapper.countRecipeList(null, null, null, "반찬", null)).thenReturn(0);

        RecipeListQuery query = new RecipeListQuery(null, null, null, null, " 반찬 ", "   ", null, null);
        recipeService.getRecipeList(query, null);

        verify(recipeMapper).getRecipeList(0, 20, null, null, null, null, "반찬", null, "latest");
    }

    // sort 화이트리스트 : "popular" 만 통과, 그 외(null·오타)는 전부 "latest"
    @Test
    @DisplayName("목록: sort=popular 는 그대로, 잘못된 값은 latest 로 폴백")
    void getRecipeList_sortWhitelist() {
        when(recipeMapper.getRecipeList(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(List.of());
        when(recipeMapper.countRecipeList(any(), any(), any(), any(), any())).thenReturn(0);

        recipeService.getRecipeList(new RecipeListQuery(null, null, null, null, null, null, "popular", null), null);
        recipeService.getRecipeList(new RecipeListQuery(null, null, null, null, null, null, "POPULAR", null), null);
        recipeService.getRecipeList(new RecipeListQuery(null, null, null, null, null, null, "asdf", null), null);
        recipeService.getRecipeList(new RecipeListQuery(null, null, null, null, null, null, null, null), null);

        verify(recipeMapper).getRecipeList(0, 20, null, null, null, null, null, null, "popular");
        verify(recipeMapper, times(3)).getRecipeList(0, 20, null, null, null, null, null, null, "latest");
    }

    // ---- 내 레시피 조회 (GET /api/recipes/me) ----

    private RecipeListItem myItem(long recipeNo) {
        return new RecipeListItem(recipeNo, "내 레시피" + recipeNo, "main.jpg",
                "https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/7/x.jpg", "테스터",
                LocalDate.of(2026, 9, 1), "반찬", "굽기", 210.0, "닭가슴살", 3L, false);
    }

    // 정상: 소유 memberNo + offset·size 로 매퍼 호출, pageInfo 계산
    @Test
    @DisplayName("내 레시피: 소유 memberNo·offset·size 로 매퍼 호출 + offset·totalPages 계산")
    void getMyRecipeList_success() {
        when(recipeMapper.countMyRecipeList(7L)).thenReturn(25);
        when(recipeMapper.getMyRecipeList(0, 20, 7L)).thenReturn(List.of(myItem(2), myItem(1)));

        RecipeListResponse res = recipeService.getMyRecipeList(7L, 0, 20);

        assertThat(res.recipes()).hasSize(2);
        assertThat(res.pageInfo().getOffset()).isEqualTo(0);
        assertThat(res.pageInfo().getTotalElements()).isEqualTo(25);
        assertThat(res.pageInfo().getTotalPages()).isEqualTo(2); // ceil(25/20)
        verify(recipeMapper).getMyRecipeList(0, 20, 7L);
    }

    // 작성한 레시피 0건 → 빈 리스트 (에러 아님)
    @Test
    @DisplayName("내 레시피: 작성 레시피 0건이면 빈 리스트 반환")
    void getMyRecipeList_empty() {
        when(recipeMapper.countMyRecipeList(7L)).thenReturn(0);
        when(recipeMapper.getMyRecipeList(0, 20, 7L)).thenReturn(List.of());

        RecipeListResponse res = recipeService.getMyRecipeList(7L, 0, 20);

        assertThat(res.recipes()).isEmpty();
        assertThat(res.pageInfo().getTotalPages()).isZero();
    }

    // page 음수 → INVALID_INPUT_VALUE + data{page}, 매퍼 미호출
    @Test
    @DisplayName("내 레시피: page 음수면 INVALID_INPUT_VALUE + data{page}, 매퍼 미호출")
    void getMyRecipeList_negativePage() {
        assertThatThrownBy(() -> recipeService.getMyRecipeList(7L, -1, 20))
                .isInstanceOfSatisfying(CustomException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                    assertThat(ex.getDetails()).containsKey("page");
                });
        verify(recipeMapper, never()).countMyRecipeList(anyLong());
    }

    // size 범위 밖 → INVALID_INPUT_VALUE + data{size}, 매퍼 미호출
    @Test
    @DisplayName("내 레시피: size 범위 밖이면 INVALID_INPUT_VALUE + data{size}, 매퍼 미호출")
    void getMyRecipeList_sizeOutOfRange() {
        assertThatThrownBy(() -> recipeService.getMyRecipeList(7L, 0, 51))
                .isInstanceOfSatisfying(CustomException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                    assertThat(ex.getDetails()).containsKey("size");
                });
        verify(recipeMapper, never()).countMyRecipeList(anyLong());
    }

    // 마지막 페이지 초과(page == totalPages) → INVALID_INPUT_VALUE + data{page}, 목록 매퍼 미호출
    @Test
    @DisplayName("내 레시피: 마지막 페이지 초과면 INVALID_INPUT_VALUE + data{page}")
    void getMyRecipeList_pageOvershoot() {
        when(recipeMapper.countMyRecipeList(7L)).thenReturn(5); // totalPages = 1

        assertThatThrownBy(() -> recipeService.getMyRecipeList(7L, 1, 20))
                .isInstanceOfSatisfying(CustomException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                    assertThat(ex.getDetails()).containsKey("page");
                });
        verify(recipeMapper, never()).getMyRecipeList(anyInt(), anyInt(), anyLong());
    }

    // ---- 상세 조회 ----

    private RecipeDetailItem detailRow() {
        return new RecipeDetailItem(5L, 7L, "된장국", "나트륨 줄인 된장국",             // recipeNo, memberNo(작성자)
                "main.jpg",                                                         // RECIPE_MAIN_IMG  (원본명)
                "https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/7/main.jpg", // RECIPES_IMG_PATH (URL)
                "관리자", LocalDate.of(2026, 8, 21),
                "끓이기", "국&찌개",                                                  // cookingMethod, recipeType
                120.5, 10.0, 8.0, 3.0, 400.0,                                       // calorie~sodium
                "두부", 34L,                                                         // mainMaterial, viewCount
                false);
    }

    // 정상: recipe + materials + steps 를 매퍼 3개에서 받아 조립. steps 는 매퍼 정렬 순서 유지.
    @Test
    @DisplayName("상세 조회: 3개 쿼리 결과를 recipe/materials/steps 로 조립")
    void getRecipe_assembles() {
        when(recipeMapper.getRecipeDetail(5L, null)).thenReturn(detailRow());
        when(recipeMapper.getMaterialsByRecipeNo(5L)).thenReturn(List.of(
                Material.builder().materialNo(1L).recipeNo(5L).materialName("두부").amount("20g").build(),
                Material.builder().materialNo(2L).recipeNo(5L).materialName("감자").amount("10g").build()));
        when(recipeMapper.getStepsByRecipeNo(5L)).thenReturn(List.of(
                RecipeStep.builder().stepNo(10L).recipeNo(5L).stepInfo("썬다").stepOrder(1)
                        .stepImg("s1.jpg").stepImgPath("https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/steps/5/s1.jpg").build(),
                RecipeStep.builder().stepNo(11L).recipeNo(5L).stepInfo("끓인다").stepOrder(2).build()));

        RecipeDetailResponse res = recipeService.getRecipe(5L, null);       // 비로그인 → memberNo null

        assertThat(res.recipe().recipeNo()).isEqualTo(5L);
        assertThat(res.recipe().memberNo()).isEqualTo(7L);                 // 작성자 PK
        assertThat(res.recipe().recipeMainImg()).isEqualTo("main.jpg");    // RECIPE_MAIN_IMG (원본명)
        assertThat(res.recipe().recipesImgPath()).startsWith("https://");  // RECIPES_IMG_PATH (버킷 URL)
        assertThat(res.recipe().cookingMethod()).isEqualTo("끓이기");
        assertThat(res.recipe().recipeType()).isEqualTo("국&찌개");
        assertThat(res.recipe().calorie()).isEqualTo(120.5);
        assertThat(res.recipe().viewCount()).isEqualTo(34L);
        assertThat(res.recipe().isBookmarked()).isFalse();                 // 매퍼 mock 이 false 로 내려줌
        verify(recipeMapper).getRecipeDetail(5L, null);                    // 비로그인이면 memberNo=null 로 매퍼 호출
        verify(recipeMapper).increaseViewCount(5L);                        // 조회수 +1
        assertThat(res.materials()).extracting(m -> m.materialName()).containsExactly("두부", "감자");
        assertThat(res.steps()).hasSize(2);
        assertThat(res.steps().get(0).stepImg()).isEqualTo("s1.jpg");      // STEP_IMG (원본명)
        assertThat(res.steps().get(0).stepImgPath())
                .isEqualTo("https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/steps/5/s1.jpg"); // STEP_IMG_PATH (URL)
        assertThat(res.steps().get(1).stepImg()).isNull();                 // 이미지 없는 단계
        assertThat(res.steps().get(1).stepImgPath()).isNull();
        assertThat(res.steps().get(1).stepInfo()).isEqualTo("끓인다");
    }

    // 없는 레시피 → RECIPE_NOT_FOUND, 재료·단계 쿼리는 아예 안 나감
    @Test
    @DisplayName("상세 조회: 없는 레시피면 RECIPE_NOT_FOUND, 자식 쿼리 미실행")
    void getRecipe_notFound() {
        when(recipeMapper.getRecipeDetail(999L, null)).thenReturn(null);

        assertThatThrownBy(() -> recipeService.getRecipe(999L, null))
                .isInstanceOfSatisfying(CustomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RECIPE_NOT_FOUND));

        verify(recipeMapper, never()).increaseViewCount(anyLong());   // 없는 레시피엔 조회수 안 올림
        verify(recipeMapper, never()).getMaterialsByRecipeNo(anyLong());
        verify(recipeMapper, never()).getStepsByRecipeNo(anyLong());
    }

    // 조회수 UPDATE 가 터져도 catch 로 삼켜서 상세 조회는 정상 반환
    @Test
    @DisplayName("상세 조회: viewCount 증가 실패해도 조회는 성공")
    void getRecipe_viewCountFailure_stillReturns() {
        when(recipeMapper.getRecipeDetail(5L, null)).thenReturn(detailRow());
        doThrow(new RuntimeException("락 타임아웃")).when(recipeMapper).increaseViewCount(5L);

        RecipeDetailResponse res = recipeService.getRecipe(5L, null);

        assertThat(res.recipe().recipeNo()).isEqualTo(5L);   // 예외 없이 반환됨
        verify(recipeMapper).getMaterialsByRecipeNo(5L);     // 뒤 조립도 계속 진행
    }

    // 로그인 회원 상세 조회 → memberNo 를 매퍼로 전달하고, 매퍼가 판정한 isBookmarked 를 그대로 노출
    @Test
    @DisplayName("상세 조회(로그인): memberNo 로 매퍼 호출 + isBookmarked=true 전달")
    void getRecipe_loggedIn_marksBookmarked() {
        RecipeDetailItem bookmarked = new RecipeDetailItem(5L, 7L, "된장국", "나트륨 줄인 된장국",
                "main.jpg", "https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/7/main.jpg",
                "관리자", LocalDate.of(2026, 8, 21), "끓이기", "국&찌개",
                120.5, 10.0, 8.0, 3.0, 400.0, "두부", 34L,
                true);                                                     // 매퍼가 IS_BOOKMARKED=1 로 내려준 상황
        when(recipeMapper.getRecipeDetail(5L, 42L)).thenReturn(bookmarked);

        RecipeDetailResponse res = recipeService.getRecipe(5L, 42L);

        assertThat(res.recipe().isBookmarked()).isTrue();
        verify(recipeMapper).getRecipeDetail(5L, 42L);
    }

    // ---- 레시피 수정 (updateRecipe) ----

    private static final long RID = 5L;

    private RecipeUpdateRequest updateRequest(List<MaterialUpdateRequest> materials, List<StepUpdateRequest> steps) {
        return new RecipeUpdateRequest("김치찌개", "묵은지로 끓인 김치찌개",
                "끓이기", "국&찌개",                  // cookingMethod, recipeType
                150.0, 12.0, 9.0, 4.0, 500.0,       // calorie~sodium
                "묵은지",                            // mainMaterial
                materials, steps);
    }

    private MaterialUpdateRequest mat(Long no, String name, String amount) {
        return new MaterialUpdateRequest(no, name, amount);
    }

    private StepUpdateRequest ustep(Long no, int order, String info, MultipartFile stepImg) {
        return new StepUpdateRequest(no, order, info, stepImg, null);
    }

    // 로그인 회원(MEMBER_NO=7) 소유 + 기존 대표 이미지가 있는 레시피
    private Recipe ownedRecipe() {
        return Recipe.builder()
                .recipeNo(RID).memberNo(MEMBER_NO)
                .recipeMainImg("old-main.jpg")
                .recipesImgPath("https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/7/old-main.jpg")
                .delYn("N").build();
    }

    @Test
    @DisplayName("수정: 없는 레시피면 RECIPE_NOT_FOUND, 쓰기 매퍼 미호출")
    void updateRecipe_notFound() {
        when(recipeMapper.getRecipeByNo(RID)).thenReturn(null);

        assertThatThrownBy(() -> recipeService.updateRecipe(RID,
                updateRequest(List.of(mat(null, "김치", "200g")), List.of(ustep(null, 1, "끓인다", null))),
                null, MEMBER_NO))
                .isInstanceOfSatisfying(CustomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RECIPE_NOT_FOUND));

        verify(recipeMapper, never()).updateRecipe(any());
    }

    @Test
    @DisplayName("수정: 작성자 본인이 아니면 FORBIDDEN")
    void updateRecipe_notOwner() {
        when(recipeMapper.getRecipeByNo(RID)).thenReturn(Recipe.builder()
                .recipeNo(RID).memberNo(999L).delYn("N").build());

        assertThatThrownBy(() -> recipeService.updateRecipe(RID,
                updateRequest(List.of(mat(null, "김치", "200g")), List.of(ustep(null, 1, "끓인다", null))),
                null, MEMBER_NO))
                .isInstanceOfSatisfying(CustomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(recipeMapper, never()).updateRecipe(any());
    }

    @Test
    @DisplayName("수정: 대표 이미지 미전송이면 기존 값 재입력, S3 업로드 없음")
    void updateRecipe_mainImageUnchanged_keepsExistingValues() {
        when(recipeMapper.getRecipeByNo(RID)).thenReturn(ownedRecipe());

        recipeService.updateRecipe(RID,
                updateRequest(List.of(mat(null, "김치", "200g")), List.of(ustep(null, 1, "끓인다", null))),
                null, MEMBER_NO);

        ArgumentCaptor<Recipe> cap = ArgumentCaptor.forClass(Recipe.class);
        verify(recipeMapper).updateRecipe(cap.capture());
        assertThat(cap.getValue().getRecipeMainImg()).isEqualTo("old-main.jpg");   // 원본명 유지
        assertThat(cap.getValue().getRecipesImgPath())
                .isEqualTo("https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/7/old-main.jpg"); // URL 유지
        verify(s3Service, never()).upload(any(), anyString(), anyLong());
    }

    @Test
    @DisplayName("수정: 재료 대조 — UPDATE/INSERT/DELETE 분기")
    void updateRecipe_materialReconcile() {
        when(recipeMapper.getRecipeByNo(RID)).thenReturn(ownedRecipe());
        when(recipeMapper.getMaterialsByRecipeNo(RID)).thenReturn(List.of(
                Material.builder().materialNo(100L).recipeNo(RID).materialName("김치").amount("200g").build(),
                Material.builder().materialNo(101L).recipeNo(RID).materialName("두부").amount("1모").build(),
                Material.builder().materialNo(102L).recipeNo(RID).materialName("파").amount("1대").build()));

        recipeService.updateRecipe(RID,
                updateRequest(List.of(
                        mat(100L, "묵은지", "250g"),   // UPDATE
                        mat(101L, "두부", "1모"),       // UPDATE
                        mat(null, "돼지고기", "100g")), // INSERT
                        List.of(ustep(null, 1, "끓인다", null))),
                null, MEMBER_NO);

        verify(recipeMapper, times(2)).updateMaterial(any());
        verify(recipeMapper, times(1)).insertMaterial(any());
        verify(recipeMapper).deleteMaterial(102L);
    }

    @Test
    @DisplayName("수정: 다른 레시피의 materialNo면 INVALID_INPUT_VALUE")
    void updateRecipe_foreignMaterialNo() {
        when(recipeMapper.getRecipeByNo(RID)).thenReturn(ownedRecipe());
        when(recipeMapper.getMaterialsByRecipeNo(RID)).thenReturn(List.of(
                Material.builder().materialNo(100L).recipeNo(RID).materialName("김치").amount("200g").build()));

        assertThatThrownBy(() -> recipeService.updateRecipe(RID,
                updateRequest(List.of(mat(999L, "김치", "200g")), List.of(ustep(null, 1, "끓인다", null))),
                null, MEMBER_NO))
                .isInstanceOfSatisfying(CustomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    @DisplayName("수정: 단계 이미지 교체 시 새 파일 업로드(STEP_IMG=원본명) + 옛 S3 객체 삭제")
    void updateRecipe_stepImageReplaced() {
        when(recipeMapper.getRecipeByNo(RID)).thenReturn(ownedRecipe());
        when(recipeMapper.getStepsByRecipeNo(RID)).thenReturn(List.of(
                RecipeStep.builder().stepNo(200L).recipeNo(RID).stepInfo("굽는다").stepOrder(1)
                        .stepImg("old-step.jpg")
                        .stepImgPath("https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/5/old-step.jpg")
                        .build()));
        when(s3Service.upload(any(), eq("recipes/steps"), eq(RID))).thenReturn(STEP_URL);

        recipeService.updateRecipe(RID,
                updateRequest(List.of(mat(null, "김치", "200g")),
                        List.of(ustep(200L, 1, "굽는다", img("stepList[0].stepImg")))),
                null, MEMBER_NO);

        ArgumentCaptor<RecipeStep> cap = ArgumentCaptor.forClass(RecipeStep.class);
        verify(recipeMapper).updateRecipeStep(cap.capture());
        assertThat(cap.getValue().getStepImg()).isEqualTo("photo.jpg");   // 원본 파일명
        assertThat(cap.getValue().getStepImgPath()).isEqualTo(STEP_URL);  // 새 S3 버킷 URL
        verify(s3Service).delete("recipes/5/old-step.jpg");               // 트랜잭션 동기화 없어 afterCommit 즉시 실행
    }

    @Test
    @DisplayName("수정: removeStepImg=true 면 기존 단계 이미지 삭제 (컬럼 null + 옛 S3 삭제)")
    void updateRecipe_removeStepImage() {
        when(recipeMapper.getRecipeByNo(RID)).thenReturn(ownedRecipe());
        when(recipeMapper.getStepsByRecipeNo(RID)).thenReturn(List.of(
                RecipeStep.builder().stepNo(200L).recipeNo(RID).stepInfo("굽는다").stepOrder(1)
                        .stepImg("old.jpg")
                        .stepImgPath("https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/5/old.jpg")
                        .build()));

        recipeService.updateRecipe(RID,
                updateRequest(List.of(mat(null, "김치", "200g")),
                        List.of(new StepUpdateRequest(200L, 1, "굽는다", null, true))),  // 이미지 삭제
                null, MEMBER_NO);

        ArgumentCaptor<RecipeStep> cap = ArgumentCaptor.forClass(RecipeStep.class);
        verify(recipeMapper).updateRecipeStep(cap.capture());
        assertThat(cap.getValue().getStepImg()).isNull();
        assertThat(cap.getValue().getStepImgPath()).isNull();
        verify(s3Service).delete("recipes/5/old.jpg");            // 옛 S3 객체 정리
        verify(s3Service, never()).upload(any(), anyString(), anyLong());
    }

    @Test
    @DisplayName("수정: 빠진 단계는 행·이미지 삭제, 남는 단계 있으면 bumpStepOrders")
    void updateRecipe_removedStep_deletesRowAndImage() {
        when(recipeMapper.getRecipeByNo(RID)).thenReturn(ownedRecipe());
        when(recipeMapper.getStepsByRecipeNo(RID)).thenReturn(List.of(
                RecipeStep.builder().stepNo(200L).recipeNo(RID).stepInfo("A").stepOrder(1)
                        .stepImg("a.jpg").stepImgPath("https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/5/a.jpg").build(),
                RecipeStep.builder().stepNo(201L).recipeNo(RID).stepInfo("B").stepOrder(2)
                        .stepImg("b.jpg").stepImgPath("https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/5/b.jpg").build()));

        recipeService.updateRecipe(RID,
                updateRequest(List.of(mat(null, "김치", "200g")),
                        List.of(ustep(200L, 1, "A", null))),   // 201 빠짐
                null, MEMBER_NO);

        verify(recipeMapper).deleteRecipeStep(201L);
        verify(recipeMapper).bumpStepOrders(RID);
        verify(recipeMapper).updateRecipeStep(any());
        verify(s3Service).delete("recipes/5/b.jpg");
        verify(s3Service, never()).delete("recipes/5/a.jpg");
    }

    @Test
    @DisplayName("수정: 살아남는 기존 단계가 없으면 bumpStepOrders 생략")
    void updateRecipe_allNewSteps_noBump() {
        when(recipeMapper.getRecipeByNo(RID)).thenReturn(ownedRecipe());

        recipeService.updateRecipe(RID,
                updateRequest(List.of(mat(null, "김치", "200g")),
                        List.of(ustep(null, 1, "A", null), ustep(null, 2, "B", null))),
                null, MEMBER_NO);

        verify(recipeMapper, never()).bumpStepOrders(anyLong());
        verify(recipeMapper, times(2)).insertRecipeStep(any());
    }

    @Test
    @DisplayName("수정: STEP_ORDER 중복이면 INVALID_INPUT_VALUE, 조회 미실행")
    void updateRecipe_duplicateStepOrder() {
        assertThatThrownBy(() -> recipeService.updateRecipe(RID,
                updateRequest(List.of(mat(null, "김치", "200g")),
                        List.of(ustep(null, 1, "A", null), ustep(null, 1, "B", null))),
                null, MEMBER_NO))
                .isInstanceOf(CustomException.class);

        verify(recipeMapper, never()).getRecipeByNo(anyLong());
    }

    // ---- 레시피 삭제 (deleteRecipe) — 소프트 삭제 ----

    @Test
    @DisplayName("삭제: 작성자 본인이면 DEL_YN='Y' UPDATE, 자식·S3 는 안 건드림")
    void deleteRecipe_softDeletes() {
        when(recipeMapper.getRecipeByNo(RID)).thenReturn(ownedRecipe());

        recipeService.deleteRecipe(RID, MEMBER_NO);

        verify(recipeMapper).updateRecipeDelYn(RID, MEMBER_NO);
        verify(recipeMapper, never()).deleteMaterial(anyLong());
        verify(recipeMapper, never()).deleteRecipeStep(anyLong());
        verify(s3Service, never()).delete(anyString());
    }

    @Test
    @DisplayName("삭제: 없는 레시피면 RECIPE_NOT_FOUND, UPDATE 미실행")
    void deleteRecipe_notFound() {
        when(recipeMapper.getRecipeByNo(RID)).thenReturn(null);

        assertThatThrownBy(() -> recipeService.deleteRecipe(RID, MEMBER_NO))
                .isInstanceOfSatisfying(CustomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RECIPE_NOT_FOUND));

        verify(recipeMapper, never()).updateRecipeDelYn(anyLong(), anyLong());
    }

    @Test
    @DisplayName("삭제: 작성자 본인이 아니면 FORBIDDEN, UPDATE 미실행")
    void deleteRecipe_notOwner() {
        when(recipeMapper.getRecipeByNo(RID)).thenReturn(Recipe.builder()
                .recipeNo(RID).memberNo(999L).delYn("N").build());

        assertThatThrownBy(() -> recipeService.deleteRecipe(RID, MEMBER_NO))
                .isInstanceOfSatisfying(CustomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(recipeMapper, never()).updateRecipeDelYn(anyLong(), anyLong());
    }

    @Test
    @DisplayName("존재검증: 활성 레시피면 통과")
    void validateRecipeExists_ok() {
        when(recipeMapper.getRecipeByNo(RID)).thenReturn(ownedRecipe());

        recipeService.validateRecipeExists(RID);

        verify(recipeMapper).getRecipeByNo(RID);
    }

    @Test
    @DisplayName("존재검증: 없거나 삭제된 레시피면 RECIPE_NOT_FOUND")
    void validateRecipeExists_notFound() {
        when(recipeMapper.getRecipeByNo(RID)).thenReturn(null);

        assertThatThrownBy(() -> recipeService.validateRecipeExists(RID))
                .isInstanceOfSatisfying(CustomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RECIPE_NOT_FOUND));
    }

    @Test
    @DisplayName("존재검증: recipeNo 가 null 이면 매퍼 호출 없이 RECIPE_NOT_FOUND")
    void validateRecipeExists_null() {
        assertThatThrownBy(() -> recipeService.validateRecipeExists(null))
                .isInstanceOfSatisfying(CustomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RECIPE_NOT_FOUND));

        verify(recipeMapper, never()).getRecipeByNo(anyLong());
    }

    // ---- 칼로리 기반 추천 (GET /api/recipes/recommend/calorie) ----

    private RecipeListItem recommend(long recipeNo, double calorie) {
        return new RecipeListItem(recipeNo, "레시피" + recipeNo, "main.jpg",
                "https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/1/x.jpg", "김민재",
                LocalDate.of(2026, 9, 1), "반찬", "굽기", calorie, "닭가슴살", 0L, false);
    }

    @Test
    @DisplayName("칼로리 추천: totalCalories 2100 이면 perMeal 700 으로 매퍼 호출하고 결과를 그대로 감싼다")
    void getCalorieRecommendRecipes_success() {
        List<RecipeListItem> found = List.of(recommend(11, 690), recommend(12, 720));
        when(recipeMapper.getCalorieRecommendRecipes(MEMBER_NO, 700.0, 3)).thenReturn(found);

        RecipeRecommendResponse res = recipeService.getCalorieRecommendRecipes(MEMBER_NO, 2100.0);

        assertThat(res.recipes()).isSameAs(found);
        verify(recipeMapper).getCalorieRecommendRecipes(MEMBER_NO, 700.0, 3);
    }

    @Test
    @DisplayName("칼로리 추천: 후보가 없으면 빈 리스트 반환 (에러 아님)")
    void getCalorieRecommendRecipes_empty() {
        when(recipeMapper.getCalorieRecommendRecipes(MEMBER_NO, 700.0, 3)).thenReturn(List.of());

        RecipeRecommendResponse res = recipeService.getCalorieRecommendRecipes(MEMBER_NO, 2100.0);

        assertThat(res.recipes()).isEmpty();
    }

    @Test
    @DisplayName("칼로리 추천: totalCalories 가 null 이면 INVALID_INPUT_VALUE + data{totalCalories}, 매퍼 미호출")
    void getCalorieRecommendRecipes_null() {
        assertThatThrownBy(() -> recipeService.getCalorieRecommendRecipes(MEMBER_NO, null))
                .isInstanceOfSatisfying(CustomException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                    assertThat(ex.getDetails()).containsKey("totalCalories");
                });
        verify(recipeMapper, never()).getCalorieRecommendRecipes(anyLong(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("칼로리 추천: totalCalories 가 0 이하면 INVALID_INPUT_VALUE, 매퍼 미호출")
    void getCalorieRecommendRecipes_nonPositive() {
        assertThatThrownBy(() -> recipeService.getCalorieRecommendRecipes(MEMBER_NO, 0.0))
                .isInstanceOfSatisfying(CustomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(recipeMapper, never()).getCalorieRecommendRecipes(anyLong(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("칼로리 추천: totalCalories 가 상한(10000) 초과면 INVALID_INPUT_VALUE, 매퍼 미호출")
    void getCalorieRecommendRecipes_tooLarge() {
        assertThatThrownBy(() -> recipeService.getCalorieRecommendRecipes(MEMBER_NO, 10001.0))
                .isInstanceOfSatisfying(CustomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(recipeMapper, never()).getCalorieRecommendRecipes(anyLong(), anyDouble(), anyInt());
    }
}
