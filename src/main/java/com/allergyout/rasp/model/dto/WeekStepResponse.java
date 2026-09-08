package com.allergyout.rasp.model.dto;

import java.util.List;

// GET /api/rasp/steps/week 응답. days 는 날짜 오름차순(오늘 포함 7일), 데이터 없는 날은 생략.
public record WeekStepResponse(Long deviceNo, List<DayStep> days) {
}
