package com.allergyout.rasp.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;
import com.allergyout.rasp.model.dao.RaspMapper;
import com.allergyout.rasp.model.dto.DeviceResponse;
import com.allergyout.rasp.model.dto.StepLogCreateRequest;
import com.allergyout.rasp.model.dto.TodayStepListResponse;
import com.allergyout.rasp.model.dto.WeeklyStepListResponse;
import com.allergyout.rasp.model.vo.DailyStepCount;
import com.allergyout.rasp.model.vo.TodayStepPoint;

@ExtendWith(MockitoExtension.class)
class RaspServiceTest {

    @Mock
    private RaspMapper raspMapper;

    @InjectMocks
    private RaspService raspService;

    private static final long MEMBER_NO = 1L;

    @Test
    @DisplayName("등록: 기존 디바이스가 없으면 INSERT 후 생성된 deviceNo 를 반환한다")
    void createDevice_new() {
        when(raspMapper.getDeviceNoByMemberNo(MEMBER_NO)).thenReturn(null);
        // insertDevice 가 keyProperty 로 param 에 deviceNo 를 채우는 동작을 흉내
        doAnswer(inv -> {
            Map<String, Object> param = inv.getArgument(0);
            param.put("deviceNo", 7L);
            return null;
        }).when(raspMapper).insertDevice(anyMap());

        DeviceResponse res = raspService.createDevice(MEMBER_NO);

        assertThat(res.deviceNo()).isEqualTo(7L);
        verify(raspMapper).insertDevice(anyMap());
    }

    @Test
    @DisplayName("등록: 이미 디바이스가 있으면 INSERT 없이 기존 deviceNo 를 반환한다(멱등)")
    void createDevice_idempotent() {
        when(raspMapper.getDeviceNoByMemberNo(MEMBER_NO)).thenReturn(9L);

        DeviceResponse res = raspService.createDevice(MEMBER_NO);

        assertThat(res.deviceNo()).isEqualTo(9L);
        verify(raspMapper, never()).insertDevice(anyMap());
    }

    @Test
    @DisplayName("조회: 등록돼 있으면 deviceNo 를 반환한다")
    void getDevice_found() {
        when(raspMapper.getDeviceNoByMemberNo(MEMBER_NO)).thenReturn(7L);

        DeviceResponse res = raspService.getDevice(MEMBER_NO);

        assertThat(res.deviceNo()).isEqualTo(7L);
    }

    @Test
    @DisplayName("조회: 미등록이면 DEVICE_NOT_FOUND")
    void getDevice_notRegistered() {
        when(raspMapper.getDeviceNoByMemberNo(MEMBER_NO)).thenReturn(null);

        assertThatThrownBy(() -> raspService.getDevice(MEMBER_NO))
                .isInstanceOfSatisfying(CustomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DEVICE_NOT_FOUND));
    }

    @Test
    @DisplayName("걸음 저장: 디바이스가 존재하면 INSERT 한다")
    void createStepLog_success() {
        when(raspMapper.existsByDeviceNo(7L)).thenReturn(true);

        raspService.createStepLog(new StepLogCreateRequest(7L, 5234));

        verify(raspMapper).insertStepLog(7L, 5234);
    }

    @Test
    @DisplayName("걸음 저장: 없는 deviceNo 면 DEVICE_NOT_FOUND, INSERT 미호출")
    void createStepLog_deviceNotFound() {
        when(raspMapper.existsByDeviceNo(99L)).thenReturn(false);

        assertThatThrownBy(() -> raspService.createStepLog(new StepLogCreateRequest(99L, 5234)))
                .isInstanceOfSatisfying(CustomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DEVICE_NOT_FOUND));
        verify(raspMapper, never()).insertStepLog(any(), any());
    }

    @Test
    @DisplayName("걸음 저장: todaySteps 0 도 정상 저장 (경계)")
    void createStepLog_zeroSteps() {
        when(raspMapper.existsByDeviceNo(7L)).thenReturn(true);

        raspService.createStepLog(new StepLogCreateRequest(7L, 0));

        verify(raspMapper).insertStepLog(7L, 0);
    }

    @Test
    @DisplayName("오늘 조회: 등록돼 있으면 deviceNo + 포인트 목록을 조립해 반환한다")
    void getTodaySteps_success() {
        when(raspMapper.getDeviceNoByMemberNo(MEMBER_NO)).thenReturn(7L);
        when(raspMapper.getTodayStepPoints(7L)).thenReturn(List.of(
                new TodayStepPoint(LocalDateTime.of(2026, 9, 8, 8, 0, 0), 1200),
                new TodayStepPoint(LocalDateTime.of(2026, 9, 8, 14, 30, 0), 5234)));

        TodayStepListResponse res = raspService.getTodaySteps(MEMBER_NO);

        assertThat(res.deviceNo()).isEqualTo(7L);
        assertThat(res.points()).hasSize(2);
        assertThat(res.points().get(1).steps()).isEqualTo(5234);
        assertThat(res.points().get(1).time()).isEqualTo(LocalDateTime.of(2026, 9, 8, 14, 30, 0));
    }

    @Test
    @DisplayName("오늘 조회: 등록됐지만 걸음 0건이면 빈 points (에러 아님)")
    void getTodaySteps_empty() {
        when(raspMapper.getDeviceNoByMemberNo(MEMBER_NO)).thenReturn(7L);
        when(raspMapper.getTodayStepPoints(7L)).thenReturn(List.of());

        TodayStepListResponse res = raspService.getTodaySteps(MEMBER_NO);

        assertThat(res.deviceNo()).isEqualTo(7L);
        assertThat(res.points()).isEmpty();
    }

    @Test
    @DisplayName("오늘 조회: 미등록이면 DEVICE_NOT_FOUND, 걸음 쿼리 미호출")
    void getTodaySteps_notRegistered() {
        when(raspMapper.getDeviceNoByMemberNo(MEMBER_NO)).thenReturn(null);

        assertThatThrownBy(() -> raspService.getTodaySteps(MEMBER_NO))
                .isInstanceOfSatisfying(CustomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DEVICE_NOT_FOUND));
        verify(raspMapper, never()).getTodayStepPoints(any());
    }

    @Test
    @DisplayName("주간 조회: 등록돼 있으면 deviceNo + 일자별 목록을 조립해 반환한다")
    void getWeekSteps_success() {
        when(raspMapper.getDeviceNoByMemberNo(MEMBER_NO)).thenReturn(7L);
        when(raspMapper.getDailyStepCounts(7L)).thenReturn(List.of(
                new DailyStepCount(LocalDate.of(2026, 9, 2), 9210),
                new DailyStepCount(LocalDate.of(2026, 9, 8), 5234)));

        WeeklyStepListResponse res = raspService.getWeekSteps(MEMBER_NO);

        assertThat(res.deviceNo()).isEqualTo(7L);
        assertThat(res.days()).hasSize(2);
        assertThat(res.days().get(0).date()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(res.days().get(0).steps()).isEqualTo(9210);
    }

    @Test
    @DisplayName("주간 조회: 미등록이면 DEVICE_NOT_FOUND, 걸음 쿼리 미호출")
    void getWeekSteps_notRegistered() {
        when(raspMapper.getDeviceNoByMemberNo(MEMBER_NO)).thenReturn(null);

        assertThatThrownBy(() -> raspService.getWeekSteps(MEMBER_NO))
                .isInstanceOfSatisfying(CustomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DEVICE_NOT_FOUND));
        verify(raspMapper, never()).getDailyStepCounts(any());
    }
}
