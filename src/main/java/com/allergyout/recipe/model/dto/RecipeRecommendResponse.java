package com.allergyout.recipe.model.dto;

import java.util.List;

// 오늘의 추천(GET /api/recipes/recommend) 응답의 data. { recipes: [...] } — 페이징 없음, 최대 3개.
// 카드는 RecipeRecommendItem (isBookmarked 없음). 칼로리 기반 추천은 RecipeCalorieRecommendResponse 로 별도.
public record RecipeRecommendResponse(
        List<RecipeRecommendItem> recipes
) {
}
