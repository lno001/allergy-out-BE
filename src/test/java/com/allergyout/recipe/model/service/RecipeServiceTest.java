package com.allergyout.recipe.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.allergyout.recipe.model.dto.RecipeListResponse;
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

    // ---- 목록 조회 ----

    // 비회원(memberNo == null): 알러지 없는 비회원용 매퍼(getRecipeList/countRecipeList) 호출,
    // offset = page*size 계산, count 로 totalPages(올림) 세팅
    @Test
    @DisplayName("목록 조회(비회원): 비회원 매퍼 호출 + offset·pageInfo 계산")
    void getRecipeList_guest() {
        List<RecipeListItem> rows = List.of(
                new RecipeListItem(2L, "김치찌개", "kimchi.jpg", "https://img/2.jpg", "관리자", LocalDate.of(2026, 8, 21)),
                new RecipeListItem(1L, "된장국", "doenjang.jpg", "https://img/1.jpg", "관리자", LocalDate.of(2026, 8, 20)));
        when(recipeMapper.getRecipeList(20, 10)).thenReturn(rows); // page 2 * size 10 = offset 20
        when(recipeMapper.countRecipeList()).thenReturn(37);

        RecipeListResponse res = recipeService.getRecipeList(2, 10, null);

        assertThat(res.recipes()).hasSize(2);
        assertThat(res.pageInfo().getOffset()).isEqualTo(20);
        assertThat(res.pageInfo().getTotalElements()).isEqualTo(37);
        assertThat(res.pageInfo().getTotalPages()).isEqualTo(4); // ceil(37/10)
        verify(recipeMapper, never()).getRecipeListForMember(anyInt(), anyInt(), anyLong());
    }

    // 회원(memberNo != null): 회원용 매퍼(getRecipeListForMember/countRecipeListForMember)로 분기
    @Test
    @DisplayName("목록 조회(회원): 회원 매퍼로 분기 호출")
    void getRecipeList_member() {
        when(recipeMapper.getRecipeListForMember(0, 20, 7L)).thenReturn(List.of());
        when(recipeMapper.countRecipeListForMember(7L)).thenReturn(0);

        recipeService.getRecipeList(0, 20, 7L);

        verify(recipeMapper).getRecipeListForMember(0, 20, 7L);
        verify(recipeMapper).countRecipeListForMember(7L);
        verify(recipeMapper, never()).getRecipeList(anyInt(), anyInt());
    }

    // page 음수는 Service 에서 차단 (매퍼 미호출)
    @Test
    @DisplayName("page 음수면 CustomException, 매퍼 미호출")
    void getRecipeList_negativePage() {
        assertThatThrownBy(() -> recipeService.getRecipeList(-1, 10, null))
                .isInstanceOf(CustomException.class);
        verify(recipeMapper, never()).getRecipeList(anyInt(), anyInt());
    }

    // size 상한(50) 초과도 차단
    @Test
    @DisplayName("size가 50 초과면 CustomException")
    void getRecipeList_sizeTooLarge() {
        assertThatThrownBy(() -> recipeService.getRecipeList(0, 51, null))
                .isInstanceOf(CustomException.class);
    }

    // ---- 상세 조회 ----

    private RecipeDetailItem detailRow() {
        return new RecipeDetailItem(5L, 7L, "된장국", "나트륨 줄인 된장국",             // recipeNo, memberNo(작성자)
                "main.jpg",                                                         // RECIPE_MAIN_IMG  (원본명)
                "https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/7/main.jpg", // RECIPES_IMG_PATH (URL)
                "관리자", LocalDate.of(2026, 8, 21), false);
    }

    // 정상: recipe + materials + steps 를 매퍼 3개에서 받아 조립. steps 는 매퍼 정렬 순서 유지.
    @Test
    @DisplayName("상세 조회: 3개 쿼리 결과를 recipe/materials/steps 로 조립")
    void getRecipe_assembles() {
        when(recipeMapper.getRecipeDetail(5L)).thenReturn(detailRow());
        when(recipeMapper.getMaterialsByRecipeNo(5L)).thenReturn(List.of(
                Material.builder().materialNo(1L).recipeNo(5L).materialName("두부").amount("20g").build(),
                Material.builder().materialNo(2L).recipeNo(5L).materialName("감자").amount("10g").build()));
        when(recipeMapper.getStepsByRecipeNo(5L)).thenReturn(List.of(
                RecipeStep.builder().stepNo(10L).recipeNo(5L).stepInfo("썬다").stepOrder(1)
                        .stepImg("s1.jpg").stepImgPath("https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/steps/5/s1.jpg").build(),
                RecipeStep.builder().stepNo(11L).recipeNo(5L).stepInfo("끓인다").stepOrder(2).build()));

        RecipeDetailResponse res = recipeService.getRecipe(5L);

        assertThat(res.recipe().recipeNo()).isEqualTo(5L);
        assertThat(res.recipe().memberNo()).isEqualTo(7L);                 // 작성자 PK
        assertThat(res.recipe().recipeMainImg()).isEqualTo("main.jpg");    // RECIPE_MAIN_IMG (원본명)
        assertThat(res.recipe().recipesImgPath()).startsWith("https://");  // RECIPES_IMG_PATH (버킷 URL)
        assertThat(res.recipe().isBookmarked()).isFalse();                 // 미구현 → false
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
        when(recipeMapper.getRecipeDetail(999L)).thenReturn(null);

        assertThatThrownBy(() -> recipeService.getRecipe(999L))
                .isInstanceOfSatisfying(CustomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RECIPE_NOT_FOUND));

        verify(recipeMapper, never()).getMaterialsByRecipeNo(anyLong());
        verify(recipeMapper, never()).getStepsByRecipeNo(anyLong());
    }

    // ---- 레시피 수정 (updateRecipe) ----

    private static final long RID = 5L;

    private RecipeUpdateRequest updateRequest(List<MaterialUpdateRequest> materials, List<StepUpdateRequest> steps) {
        return new RecipeUpdateRequest("김치찌개", "묵은지로 끓인 김치찌개", materials, steps);
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
}
