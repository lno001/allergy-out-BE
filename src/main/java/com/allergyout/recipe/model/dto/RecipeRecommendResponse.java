package com.allergyout.recipe.model.dto;

import java.util.List;

public record RecipeRecommendResponse(
        List<RecipeListItem> recipes
) {
}