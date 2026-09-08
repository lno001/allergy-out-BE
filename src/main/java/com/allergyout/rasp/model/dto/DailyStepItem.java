package com.allergyout.rasp.model.dto;

import java.time.LocalDate;

import com.allergyout.rasp.model.vo.DailyStepCount;

// 지난 7일 막대 하나. date = 날짜, steps = 그날 총 걸음.
public record DailyStepItem(LocalDate date, Integer steps) {

    public static DailyStepItem from(DailyStepCount vo) {
        return new DailyStepItem(vo.getStepDate(), vo.getSteps());
    }
}
