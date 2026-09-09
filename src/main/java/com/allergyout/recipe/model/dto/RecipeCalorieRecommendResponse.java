package com.allergyout.recipe.model.dto;

import java.util.List;

// 칼로리 기반 추천(GET /api/recipes/recommend/calorie) 응답의 data. { recipes: [...] } — 페이징 없음, 최대 3개.
// 목록·상세와 동일한 RecipeListItem (isBookmarked 포함). 날짜기반 오늘의 추천(RecipeRecommendResponse)과 별개.
public record RecipeCalorieRecommendResponse(
        List<RecipeListItem> recipes
) {
}
