package com.allergyout.recipe.model.dto;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// 폼 key: stepList[i].stepNo / stepList[i].stepOrder / stepList[i].stepInfo / stepList[i].stepImg
// stepNo : 기존 단계면 그 PK(상세 조회에서 받은 값을 그대로 재전송), 새로 추가한 단계면 null.
//          → Service 가 null 이면 INSERT, 값이 있으면 UPDATE 로 분기한다.
// stepImg       : 그 단계 이미지를 "바꿨을 때만" 전송. 안 오면(null) 기존 단계는 이미지 유지, 신규 단계는 이미지 없음.
// removeStepImg : true 면 기존 단계의 이미지를 삭제 (STEP_IMG/STEP_IMG_PATH → null, 옛 S3 객체 삭제).
//                 stepImg 새 파일이 같이 오면 교체가 우선. 신규 단계에서는 의미 없음.
public record StepUpdateRequest(

        Long stepNo, // nullable — null 이면 신규 단계

        @NotNull
        @Positive
        Integer stepOrder,

        @NotBlank
        @Size(max = 2000) // RECIPE_STEPS.STEP_INFO VARCHAR2(2000)
        String stepInfo,

        MultipartFile stepImg, // 선택 — 없으면 null

        Boolean removeStepImg  // 선택 — true 면 기존 이미지 삭제 (미전송/false = 유지)
) {
}
