package com.allergyout.recipe.model.dto;

import org.springframework.web.bind.annotation.BindParam;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// 폼 key: STEP_LIST[i].STEP_ORDER / STEP_LIST[i].STEP_INFO / STEP_LIST[i].STEP_IMG
// 스텝 이미지는 선택 (RECIPE_STEPS.STEP_IMG NULLABLE). 대표 이미지만 Controller @RequestParam으로 분리해 받는다.
public record StepCreateRequest(

        @BindParam("STEP_ORDER")
        @NotNull
        @Positive
        Integer stepOrder,

        @BindParam("STEP_INFO")
        @NotBlank
        @Size(max = 2000) // RECIPE_STEPS.STEP_INFO VARCHAR2(2000)
        String stepInfo,

        @BindParam("STEP_IMG")
        MultipartFile stepImg // 선택 — 없으면 null
) {
}
