package com.allergyout.rasp.model.dto;

import java.time.LocalDate;

// GET /api/rasp/steps/week 의 하루 = 그날의 총 걸음.
// stepDate = 날짜, steps = 그날 MAX(TODAY_STEPS).
public record DayStep(LocalDate stepDate, Integer steps) {
}
