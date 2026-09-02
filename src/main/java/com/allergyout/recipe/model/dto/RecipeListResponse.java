package com.allergyout.recipe.model.dto;

import java.util.List;

import com.allergyout.global.common.PageInfo;

// GET /api/recipes 응답의 data. { recipes: [...], pageInfo: { page, size, offset, totalElements, totalPages } }
public record RecipeListResponse(
        List<RecipeListItem> recipes,
        PageInfo pageInfo
) {
}
