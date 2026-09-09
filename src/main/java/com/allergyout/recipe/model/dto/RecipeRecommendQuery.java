package com.allergyout.recipe.model.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 추천 레시피 보여주기용
 */
public record RecipeRecommendQuery(

        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        String date,
        String keyword,

        List<String> excludeMaterials,

        @Pattern(regexp = "밥|국&찌개|반찬|일품|후식|기타")
        String recipeType,

        @Pattern(regexp = "굽기|튀기기|볶기|찌기|끓이기|기타")
        String cookingMethod,

        @Pattern(regexp = "true|false")
        String applyMyAllergy
) {
}