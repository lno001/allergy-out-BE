package com.allergyout.recipe.model.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

// 상세 조회 응답의 recipe 객체. RECIPES ⨝ MEMBER 프로젝션을 매퍼 resultType 으로 직접 매핑한다.
// recipeMainImg  = RECIPE_MAIN_IMG(원본 파일명)
// recipesImgPath = RECIPES_IMG_PATH(S3 버킷 URL — 프론트가 이미지를 띄울 주소)
// isBookmarked   = 현재 매퍼가 0(false) 고정. 즐겨찾기 기능 구현 시 Service 에서 실제 판정으로 교체.
public record RecipeDetailItem(
        Long recipeNo,        // RECIPE_NO
        String recipeTitle,   // RECIPE_TITLE
        String recipeInfo,    // RECIPE_INFO
        String recipeMainImg,  // RECIPE_MAIN_IMG  (원본 파일명)
        String recipesImgPath, // RECIPES_IMG_PATH (S3 버킷 URL)
        String memberName,    // MEMBER.MEMBER_NAME (작성자)
        LocalDate createDate, // CREATE_DATE (yyyy-MM-dd)

        @JsonProperty("isBookmarked") // is 접두사 프로퍼티명 그대로 직렬화 (명세서 필드명 = isBookmarked)
        boolean isBookmarked  // 로그인 회원의 즐겨찾기 여부 (미구현 → false 고정)
) {
}
