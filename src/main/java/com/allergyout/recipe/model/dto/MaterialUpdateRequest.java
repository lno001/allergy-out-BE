package com.allergyout.recipe.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 폼 key: materialList[i].materialNo / materialList[i].materialName / materialList[i].amount
// materialNo: 기존 재료면 그 PK(상세 조회에서 받은 값을 그대로 재전송), 새로 추가한 재료면 null.
//             → Service 가 null 이면 INSERT, 값이 있으면 UPDATE 로 분기한다.
public record MaterialUpdateRequest(

        Long materialNo, // nullable — null 이면 신규 재료

        @NotBlank
        @Size(max = 30) // MATERIAL.MATERIAL_NAME NVARCHAR2(30)
        String materialName,

        @NotBlank
        @Size(max = 30) // MATERIAL.AMOUNT NVARCHAR2(30)
        String amount) {
}
