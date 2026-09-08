package com.allergyout.rasp.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// POST /api/rasp/steps 요청 (라즈베리파이 → 서버, permitAll). body 의 deviceNo 가 곧 신분.
public record StepLogCreateRequest(

        @NotNull(message = "deviceNo는 필수입니다.")
        Long deviceNo,                                   // DEVICE.DEVICE_NO

        @NotNull(message = "todaySteps는 필수입니다.")
        @PositiveOrZero(message = "todaySteps는 0 이상이어야 합니다.")
        Integer todaySteps                               // STEP_LOG.TODAY_STEPS NUMBER (그날 누적)
) {
}
