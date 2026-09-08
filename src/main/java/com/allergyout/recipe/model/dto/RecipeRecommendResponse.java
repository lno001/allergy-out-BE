package com.allergyout.recipe.model.dto;

import java.util.List;

// GET /api/recipes/recommend 응답의 data. { recipes: [...] } — 페이징 없음. 후보 없으면 빈 리스트.
public record RecipeRecommendResponse(List<RecipeRecommendItem> recipes) {
}
