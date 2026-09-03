package com.allergyout.recipe.model.dto;

import java.util.List;

import com.allergyout.recipe.model.vo.Material;
import com.allergyout.recipe.model.vo.RecipeStep;

// GET /api/recipes/{recipeNo} 응답의 data. 집계 조회이므로 다중 쿼리 결과를 Service 가 조립한다.
public record RecipeDetailResponse(
        RecipeDetailItem recipe,
        List<MaterialItem> materials,
        List<StepItem> steps
) {
    public static RecipeDetailResponse of(RecipeDetailItem recipe,
                                          List<Material> materials,
                                          List<RecipeStep> steps) {
        return new RecipeDetailResponse(
                recipe,
                materials.stream().map(MaterialItem::from).toList(),
                steps.stream().map(StepItem::from).toList());
    }
}
