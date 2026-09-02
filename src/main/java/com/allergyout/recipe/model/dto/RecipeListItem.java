package com.allergyout.recipe.model.dto;

import java.time.LocalDate;

// 목록 조회 응답의 레시피 1건. RECIPES ⨝ MEMBER 프로젝션을 매퍼 resultType 으로 직접 매핑한다.
public record RecipeListItem(
        Long recipeNo,          // RECIPE_NO
        String recipeTitle,     // RECIPE_TITLE
        String recipeMainImg,   // RECIPE_MAIN_IMG (S3 URL)
        String memberName,      // MEMBER.MEMBER_NAME (작성자)
        LocalDate createDate    // CREATE_DATE (yyyy-MM-dd)
) {
}
