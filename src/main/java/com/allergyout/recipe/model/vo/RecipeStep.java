package com.allergyout.recipe.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// MyBatis로 매핑되는 VO - RECIPE_STEPS 테이블과 1:1, 불변(setter 없음)
@Getter
@Builder
@AllArgsConstructor
public class RecipeStep {

    private final Long stepNo;        // STEP_NO (PK, IDENTITY)
    private final Long recipeNo;      // RECIPE_NO (FK)
    private final String stepInfo;    // STEP_INFO VARCHAR2(2000) NOT NULL
    private final String stepImg;     // STEP_IMG VARCHAR2(300) NULLABLE (S3 URL, 스텝 이미지 선택)
    private final Integer stepOrder;  // STEP_ORDER NUMBER (RECIPE_NO와 UNIQUE)
    private final String stepImgPath; // STEP_IMG_PATH NVARCHAR2(300) NULLABLE (원본 파일명, ALTER로 NULL 허용 적용됨)
}
