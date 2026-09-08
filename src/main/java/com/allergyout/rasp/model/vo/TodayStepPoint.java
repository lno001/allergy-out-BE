package com.allergyout.rasp.model.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// STEP_LOG 한 행 (오늘 조회용 인트라데이 포인트). 매퍼 생성자 매핑.
@Getter
@Builder
@AllArgsConstructor
public class TodayStepPoint {

    private final LocalDateTime createDate; // STEP_LOG.CREATE_DATE
    private final Integer todaySteps;       // STEP_LOG.TODAY_STEPS (그날 누적)
}
