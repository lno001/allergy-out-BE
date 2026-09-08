package com.allergyout.rasp.model.dto;

import java.time.LocalDateTime;

// GET /api/rasp/steps/day 의 한 점 = STEP_LOG 한 보고.
// createDate = 보고 시각, steps = 그 시점의 그날 누적 걸음.
public record StepPoint(LocalDateTime createDate, Integer steps) {
}
