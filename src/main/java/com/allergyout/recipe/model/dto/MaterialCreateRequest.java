package com.allergyout.recipe.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 폼 key: materialList[i].materialName / materialList[i].amount
public record MaterialCreateRequest(

        @NotBlank
        @Size(max = 30) // MATERIAL.MATERIAL_NAME NVARCHAR2(30) (명세서 200자 표기와 다름 — DDL 기준)
        String materialName,

        @NotBlank
        @Size(max = 30) // MATERIAL.AMOUNT NVARCHAR2(30) (명세서 100자 표기와 다름 — DDL 기준)
        String amount
) {
}
