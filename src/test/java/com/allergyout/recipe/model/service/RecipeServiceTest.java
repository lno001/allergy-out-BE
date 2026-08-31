package com.allergyout.recipe.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.allergyout.recipe.model.dao.RecipeMapper;
import com.allergyout.recipe.model.dto.MaterialCreateRequest;
import com.allergyout.recipe.model.dto.RecipeCreateRequest;
import com.allergyout.recipe.model.dto.StepCreateRequest;
import com.allergyout.recipe.model.vo.RecipeStep;
import com.allergyout.s3.S3Service;

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

    private void stubRecipeInsertReturnsKey(long recipeNo) {
        doAnswer(inv -> {
            Map<String, Object> p = inv.getArgument(0);
            p.put("recipeNo", recipeNo);
            return null;
        }).when(recipeMapper).insertRecipe(any());
    }

    @Test
    @DisplayName("정상 등록: RECIPES 1회 · MATERIAL 1회 · RECIPE_STEPS 2회 매퍼 호출")
    void createRecipe_success() {
        when(s3Service.upload(any(), anyString(), anyLong())).thenReturn(MAIN_URL);
        stubRecipeInsertReturnsKey(100L);

        recipeService.createRecipe(
                request(List.of(step(1, "재료를 썬다", null), step(2, "끓인다", null))),
                img("RECIPE_MAIN_IMG"), MEMBER_NO);

        verify(recipeMapper).insertRecipe(any());
        verify(recipeMapper, times(1)).insertMaterial(any());
        verify(recipeMapper, times(2)).insertRecipeStep(any());
    }

    @Test
    @DisplayName("대표 이미지 없으면 CustomException, 매퍼 미호출")
    void createRecipe_noMainImg() {
        assertThatThrownBy(() -> recipeService.createRecipe(
                request(List.of(step(1, "...", null))), null, MEMBER_NO))
                .isInstanceOf(CustomException.class);

        verify(recipeMapper, never()).insertRecipe(any());
    }

    @Test
    @DisplayName("STEP_ORDER 중복이면 CustomException, 매퍼 미호출")
    void createRecipe_duplicateStepOrder() {
        assertThatThrownBy(() -> recipeService.createRecipe(
                request(List.of(step(1, "a", null), step(1, "b", null))),
                img("RECIPE_MAIN_IMG"), MEMBER_NO))
                .isInstanceOf(CustomException.class);

        verify(recipeMapper, never()).insertRecipe(any());
    }

    @Test
    @DisplayName("스텝 이미지 없으면 STEP_IMG/STEP_IMG_PATH는 null, S3 업로드는 대표 이미지 1회")
    void createRecipe_noStepImg_savesNull() {
        when(s3Service.upload(any(), anyString(), anyLong())).thenReturn(MAIN_URL);
        stubRecipeInsertReturnsKey(100L);

        recipeService.createRecipe(
                request(List.of(step(1, "재료를 썬다", null))),
                img("RECIPE_MAIN_IMG"), MEMBER_NO);

        ArgumentCaptor<RecipeStep> captor = ArgumentCaptor.forClass(RecipeStep.class);
        verify(recipeMapper).insertRecipeStep(captor.capture());
        assertThat(captor.getValue().getStepImg()).isNull();
        assertThat(captor.getValue().getStepImgPath()).isNull();
        verify(s3Service, times(1)).upload(any(), anyString(), anyLong());
    }

    @Test
    @DisplayName("스텝 이미지 있으면 recipes/steps 디렉터리로 업로드하고 STEP_IMG에 URL 저장")
    void createRecipe_withStepImg_uploadsAndSavesUrl() {
        when(s3Service.upload(any(), eq("recipes"), anyLong())).thenReturn(MAIN_URL);
        when(s3Service.upload(any(), eq("recipes/steps"), anyLong())).thenReturn(STEP_URL);
        stubRecipeInsertReturnsKey(100L);

        recipeService.createRecipe(
                request(List.of(step(1, "재료를 썬다", img("STEP_LIST[0].STEP_IMG")))),
                img("RECIPE_MAIN_IMG"), MEMBER_NO);

        ArgumentCaptor<RecipeStep> captor = ArgumentCaptor.forClass(RecipeStep.class);
        verify(recipeMapper).insertRecipeStep(captor.capture());
        assertThat(captor.getValue().getStepImg()).isEqualTo(STEP_URL);
        assertThat(captor.getValue().getStepImgPath()).isEqualTo("photo.jpg");
        verify(s3Service).upload(any(), eq("recipes/steps"), eq(100L));
    }

    @Test
    @DisplayName("중간에 매퍼가 실패하면 업로드된 S3 파일을 보상 삭제하고 예외 전파")
    void createRecipe_midFailure_compensatesS3() {
        when(s3Service.upload(any(), anyString(), anyLong())).thenReturn(MAIN_URL);
        stubRecipeInsertReturnsKey(100L);
        doThrow(new RuntimeException("DB 실패")).when(recipeMapper).insertMaterial(any());

        assertThatThrownBy(() -> recipeService.createRecipe(
                request(List.of(step(1, "...", null))),
                img("RECIPE_MAIN_IMG"), MEMBER_NO))
                .isInstanceOf(RuntimeException.class);

        verify(s3Service).delete("recipes/7/main.jpg");
    }
}
