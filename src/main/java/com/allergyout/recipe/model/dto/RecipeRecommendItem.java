package com.allergyout.recipe.model.dto;

import java.time.LocalDate;

// 오늘의 추천(GET /api/recipes/recommend) 응답의 레시피 1건.
// RecipeListItem 과 동일하지만 isBookmarked 는 제외 — 오늘의 추천에는 즐겨찾기 여부를 싣지 않는다.
// RECIPES ⨝ MEMBER 프로젝션을 매퍼 resultType 으로 직접 매핑 (map-underscore-to-camel-case).
public record RecipeRecommendItem(
        Long recipeNo,          // RECIPE_NO
        String recipeTitle,     // RECIPE_TITLE
        String recipeMainImg,   // RECIPE_MAIN_IMG  (원본 파일명)
        String recipesImgPath,  // RECIPES_IMG_PATH (S3 버킷 URL — 프론트 썸네일용)
        String memberName,      // MEMBER.MEMBER_NAME (작성자)
        LocalDate createDate,   // CREATE_DATE (yyyy-MM-dd)
        String recipeType,      // RECIPE_TYPE     (밥/국&찌개/반찬/일품/후식/기타)
        String cookingMethod,   // COOKING_METHOD  (굽기/튀기기/볶기/찌기/끓이기/기타)
        Double calorie,         // CALORIE NUMBER NULL
        String mainMaterial,    // MAIN_MATERIAL NVARCHAR2(20) NULL — 메인 재료 1개
        Long viewCount          // VIEW_COUNT NUMBER — 조회수
) {
}
