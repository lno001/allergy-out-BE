package com.allergyout.bookmark.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 폼 key = 필드명 그대로 (JSON 본문). BOOKMARK.RECIPE_NO (FK → RECIPES.RECIPE_NO)
public record BookmarkCreateRequest(

        @NotNull(message = "레시피 번호는 필수입니다.")
        @Positive(message = "레시피 번호가 올바르지 않습니다.")
        Long recipeNo
) {
}
