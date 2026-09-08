package com.allergyout.rasp.model.vo;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// 일자별 걸음 집계 (지난 7일 조회용). 매퍼 생성자 매핑.
@Getter
@Builder
@AllArgsConstructor
public class DailyStepCount {

    private final LocalDate stepDate; // TRUNC(STEP_LOG.CREATE_DATE)
    private final Integer steps;      // MAX(STEP_LOG.TODAY_STEPS) = 그날 총 걸음
}
