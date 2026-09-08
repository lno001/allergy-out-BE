package com.allergyout.rasp.model.dto;

import java.util.List;

import com.allergyout.rasp.model.vo.TodayStepPoint;

// GET /api/rasp/steps/today 응답. points 는 시간 오름차순, 데이터 없으면 빈 리스트.
public record TodayStepListResponse(Long deviceNo, List<TodayStepItem> points) {

    public static TodayStepListResponse of(Long deviceNo, List<TodayStepPoint> vos) {
        return new TodayStepListResponse(deviceNo, vos.stream().map(TodayStepItem::from).toList());
    }
}
