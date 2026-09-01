package com.allergyout.recipe.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// MyBatis로 매핑되는 VO - MATERIAL 테이블과 1:1, 불변(setter 없음)
@Getter
@Builder
@AllArgsConstructor
public class Material {

    private final Long materialNo;    // MATERIAL_NO (PK, IDENTITY)
    private final Long recipeNo;      // RECIPE_NO (FK)
    private final String materialName; // MATERIAL_NAME NVARCHAR2(30)
    private final String amount;      // AMOUNT NVARCHAR2(30)
}
