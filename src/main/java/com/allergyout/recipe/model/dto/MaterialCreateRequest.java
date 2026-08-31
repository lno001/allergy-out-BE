package com.allergyout.recipe.model.dto;

import org.springframework.web.bind.annotation.BindParam;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 폼 key: MATERIAL_LIST[i].MATERIAL_NAME / MATERIAL_LIST[i].AMOUNT
// ※ API 명세서 예시는 METERIAL_NAME 오타 → DDL 컬럼(MATERIAL_NAME) 기준으로 통일.
//    프론트 폼 key도 MATERIAL_NAME 으로 맞춰야 바인딩됨 (개발자 확인 필요).
public record MaterialCreateRequest(

        @BindParam("MATERIAL_NAME")
        @NotBlank
        @Size(max = 30) // MATERIAL.MATERIAL_NAME NVARCHAR2(30) (명세서 200자 표기와 다름 — DDL 기준)
        String materialName,

        @BindParam("AMOUNT")
        @NotBlank
        @Size(max = 30) // MATERIAL.AMOUNT NVARCHAR2(30) (명세서 100자 표기와 다름 — DDL 기준)
        String amount
) {
}
