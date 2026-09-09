package com.allergyout.bookmark.model.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

// 즐겨찾기 목록 응답의 레시피 1건. BOOKMARK ⨝ RECIPES ⨝ MEMBER 프로젝션을 매퍼 resultType 으로 직접 매핑한다.
// RecipeListItem 과 컴포넌트 순서·개수·타입이 완전히 동일해야 한다 (프론트가 목록/내레시피/즐겨찾기에서
// 같은 RecipeCard 를 재사용 + MyBatis 컬럼순서 기반 생성자 자동매핑). 필드가 바뀌면 getBookmarkList SELECT 도 같이 고칠 것.
// createDate = 레시피 작성일(RECIPES.CREATE_DATE). 즐겨찾기 목록이라 isBookmarked 는 항상 true(SQL 에서 1 고정).
public record BookmarkListItem(
        Long recipeNo,          // RECIPE_NO
        String recipeTitle,     // RECIPE_TITLE
        String recipeMainImg,   // RECIPE_MAIN_IMG  (원본 파일명)
        String recipesImgPath,  // RECIPES_IMG_PATH (S3 버킷 URL — 프론트 썸네일)
        String memberName,      // MEMBER.MEMBER_NAME (작성자)
        LocalDate createDate,   // RECIPES.CREATE_DATE (레시피 작성일, yyyy-MM-dd)
        String recipeType,      // RECIPE_TYPE     (밥/국&찌개/반찬/일품/후식/기타)
        String cookingMethod,   // COOKING_METHOD  (굽기/튀기기/볶기/찌기/끓이기/기타)
        Double calorie,         // CALORIE NUMBER NULL — 목록 카드 표시용
        String mainMaterial,    // MAIN_MATERIAL NVARCHAR2(20) NULL — 메인 재료 1개
        Long viewCount,         // VIEW_COUNT NUMBER — 조회수

        @JsonProperty("isBookmarked") // is 접두사 프로퍼티명 그대로 직렬화 (명세서 필드명 = isBookmarked)
        boolean isBookmarked    // 즐겨찾기 목록이므로 항상 true. 매퍼가 1 로 내려줌
) {
}
