package com.allergyout.recipe.model.dto;

// GET /api/recipes/recommend 응답의 레시피 1건. RECIPES ⨝ MEMBER 프로젝션 + CALORIE.
// 매퍼 resultType 으로 직접 매핑 (RecipeListItem 과 동일 방식).
public record RecipeRecommendItem(
        Long recipeNo,          // RECIPE_NO
        String recipeTitle,     // RECIPE_TITLE
        String recipesImgPath,  // RECIPES_IMG_PATH (S3 버킷 URL — 프론트 썸네일)
        String memberName,      // MEMBER.MEMBER_NAME (작성자)
        Double calorie          // RECIPES.CALORIE (끼니 목표와의 근접도를 프론트가 표시)
) {
}
