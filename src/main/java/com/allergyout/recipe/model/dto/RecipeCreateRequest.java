package com.allergyout.recipe.model.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

// multipart/form-data 폼 필드를 @ModelAttribute로 바인딩. 폼 key = 필드명(카멜케이스) 그대로.
// 예: recipeTitle / materialList[0].materialName / stepList[0].stepOrder
// 대표 이미지(recipeMainImg)만 이 DTO 밖에서 Controller @RequestParam으로 받는다.
public record RecipeCreateRequest(

        @NotBlank
        @Size(max = 50)   // RECIPES.RECIPE_TITLE NVARCHAR2(50)
        String recipeTitle,

        @NotBlank
        @Size(max = 1000) // RECIPES.RECIPE_INFO NVARCHAR2(1000)
        String recipeInfo,

        @NotEmpty
        @Valid
        List<MaterialCreateRequest> materialList,

        @NotEmpty
        @Valid
        List<StepCreateRequest> stepList
) {
}
