package com.allergyout.recipe.model.dto;

import com.allergyout.recipe.model.vo.RecipeStep;

// 상세 조회 응답의 조리 단계 1건.
// stepImg = STEP_IMG(원본 파일명) / stepImgPath = STEP_IMG_PATH(S3 버킷 URL). 이미지 없으면 둘 다 null.
public record StepItem(
        Long stepNo,      // STEP_NO
        Integer stepOrder, // STEP_ORDER
        String stepInfo,  // STEP_INFO
        String stepImg,     // STEP_IMG      (원본 파일명)
        String stepImgPath  // STEP_IMG_PATH (S3 버킷 URL — 프론트 이미지 표시용)
) {
    public static StepItem from(RecipeStep s) {
        return new StepItem(s.getStepNo(), s.getStepOrder(), s.getStepInfo(),
                s.getStepImg(), s.getStepImgPath());
    }
}
