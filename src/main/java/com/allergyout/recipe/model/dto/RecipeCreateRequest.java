package com.allergyout.recipe.model.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
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

        @NotBlank
        @Pattern(regexp = "굽기|튀기기|볶기|찌기|끓이기|기타") // RECIPES.COOKING_METHOD NVARCHAR2(20) — 6값 고정
        String cookingMethod,

        @NotBlank
        @Pattern(regexp = "밥|국&찌개|반찬|일품|후식|기타")   // RECIPES.RECIPE_TYPE NVARCHAR2(20) — 6값 고정
        String recipeType,

        @PositiveOrZero // RECIPES.CALORIE NUMBER — 음수 불가, 소수 가능, 선택(null)
        Double calorie,

        @PositiveOrZero // RECIPES.CARBOHYDRATE NUMBER
        Double carbohydrate,

        @PositiveOrZero // RECIPES.PROTEIN NUMBER
        Double protein,

        @PositiveOrZero // RECIPES.FAT NUMBER
        Double fat,

        @PositiveOrZero // RECIPES.SODIUM NUMBER
        Double sodium,

        @Size(max = 20) // RECIPES.MAIN_MATERIAL NVARCHAR2(20) — 메인 재료 1개
        String mainMaterial,

        @NotEmpty
        @Valid
        List<MaterialCreateRequest> materialList,

        @NotEmpty
        @Valid
        List<StepCreateRequest> stepList
) {
}
