package com.allergyout.rasp.model.dto;

import java.time.LocalDateTime;

import com.allergyout.rasp.model.vo.TodayStepPoint;

// 오늘 인트라데이 곡선의 한 점. time = 보고 시각, steps = 그때까지 누적.
public record TodayStepItem(LocalDateTime time, Integer steps) {

    public static TodayStepItem from(TodayStepPoint vo) {
        return new TodayStepItem(vo.getCreateDate(), vo.getTodaySteps());
    }
}
