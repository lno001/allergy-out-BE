package com.allergyout.recipe.model.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

// PATCH /api/recipes/{recipeNo} 의 multipart/form-data 폼 필드를 @ModelAttribute 로 바인딩.
// 등록(RecipeCreateRequest)과 필드 구성은 같지만, 재료·단계 항목마다 재조정용 PK(materialNo/stepNo)가 붙는다.
// (등록 요청 DTO 엔 PK 를 두지 않는 규칙 — mass assignment 방지 — 이라 수정 전용 record 를 따로 둔다.)
// 대표 이미지(recipeMainImg)는 이 DTO 밖에서 Controller @RequestParam(required = false) 로 받는다 (미변경 시 미전송).
public record RecipeUpdateRequest(

        @NotBlank
        @Size(max = 50)   // RECIPES.RECIPE_TITLE NVARCHAR2(50)
        String recipeTitle,

        @NotBlank
        @Size(max = 1000) // RECIPES.RECIPE_INFO NVARCHAR2(1000)
        String recipeInfo,

        @NotEmpty
        @Size(max = 20)   // 재료 최대 20개 (명세서 추가사항)
        @Valid
        List<MaterialUpdateRequest> materialList,

        @NotEmpty
        @Size(max = 20)   // 단계 최대 20개 (명세서 추가사항)
        @Valid
        List<StepUpdateRequest> stepList
) {
}
