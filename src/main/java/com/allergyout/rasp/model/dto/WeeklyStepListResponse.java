package com.allergyout.rasp.model.dto;

import java.util.List;

import com.allergyout.rasp.model.vo.DailyStepCount;

// GET /api/rasp/steps/week 응답. days 는 날짜 오름차순(오늘 포함 7일), 데이터 없는 날은 생략.
public record WeeklyStepListResponse(Long deviceNo, List<DailyStepItem> days) {

    public static WeeklyStepListResponse of(Long deviceNo, List<DailyStepCount> vos) {
        return new WeeklyStepListResponse(deviceNo, vos.stream().map(DailyStepItem::from).toList());
    }
}
