package com.allergyout.recipe.model.dto;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// 폼 key: stepList[i].stepOrder / stepList[i].stepInfo / stepList[i].stepImg
// 스텝 이미지는 선택 (RECIPE_STEPS.STEP_IMG NULLABLE).
public record StepCreateRequest(

        @NotNull
        @Positive
        Integer stepOrder,

        @NotBlank
        @Size(max = 2000) // RECIPE_STEPS.STEP_INFO VARCHAR2(2000)
        String stepInfo,

        MultipartFile stepImg // 선택 — 없으면 null
) {
}
