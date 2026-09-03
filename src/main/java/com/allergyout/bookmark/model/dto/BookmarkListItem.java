package com.allergyout.bookmark.model.dto;

import java.time.LocalDate;

// 즐겨찾기 목록 응답의 레시피 1건. BOOKMARK ⨝ RECIPES ⨝ MEMBER 프로젝션을 매퍼 resultType 으로 직접 매핑한다.
// RecipeListItem 과 동일 형태·컬럼매핑 (프론트 목록 렌더 통일). createDate = 레시피 작성일(RECIPES.CREATE_DATE).
public record BookmarkListItem(
        Long recipeNo,          // RECIPE_NO
        String recipeTitle,     // RECIPE_TITLE
        String recipeMainImg,   // RECIPE_MAIN_IMG  (원본 파일명)
        String recipesImgPath,  // RECIPES_IMG_PATH (S3 버킷 URL — 프론트 썸네일)
        String memberName,      // MEMBER.MEMBER_NAME (작성자)
        LocalDate createDate    // RECIPES.CREATE_DATE (레시피 작성일, yyyy-MM-dd)
) {
}
