package com.allergyout.recipe.model.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// MyBatis로 매핑되는 VO - RECIPES 테이블과 1:1, 불변(setter 없음)
@Getter
@Builder
@AllArgsConstructor
public class Recipe {

    private final Long recipeNo;            // RECIPE_NO (PK, IDENTITY)
    private final Long memberNo;            // MEMBER_NO (FK, 작성자)
    private final String recipeTitle;      // RECIPE_TITLE NVARCHAR2(50)
    private final String recipeInfo;       // RECIPE_INFO NVARCHAR2(1000)
    private final String recipeMainImg;    // RECIPE_MAIN_IMG VARCHAR2(300) NOT NULL (S3 URL)
    private final String recipesImgPath;   // RECIPES_IMG_PATH NVARCHAR2(300) NOT NULL (원본 파일명)
    private final LocalDateTime createDate; // CREATE_DATE (DB default SYSDATE)
    private final String delYn;            // DEL_YN CHAR(1) (DB default 'N')
}
