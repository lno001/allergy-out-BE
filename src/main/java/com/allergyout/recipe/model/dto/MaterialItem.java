package com.allergyout.recipe.model.dto;

import com.allergyout.recipe.model.vo.Material;

// 상세 조회 응답의 재료 1건
public record MaterialItem(
        Long materialNo,     // MATERIAL_NO
        String materialName, // MATERIAL_NAME
        String amount        // AMOUNT
) {
    public static MaterialItem from(Material m) {
        return new MaterialItem(m.getMaterialNo(), m.getMaterialName(), m.getAmount());
    }
}
