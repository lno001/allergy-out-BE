package com.allergyout.rasp.model.dto;

import java.util.List;

// GET /api/rasp/steps/day 응답. points 는 보고 시각 오름차순 (누적값이라 우상향). 데이터 없으면 빈 리스트.
public record DayStepResponse(Long deviceNo, List<StepPoint> points) {
}
