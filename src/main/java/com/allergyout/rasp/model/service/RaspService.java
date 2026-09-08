package com.allergyout.rasp.model.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;
import com.allergyout.rasp.model.dao.RaspMapper;
import com.allergyout.rasp.model.dto.DeviceResponse;
import com.allergyout.rasp.model.dto.StepLogCreateRequest;
import com.allergyout.rasp.model.dto.TodayStepListResponse;
import com.allergyout.rasp.model.dto.WeeklyStepListResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RaspService {

    private final RaspMapper raspMapper;

    // 로그인 회원의 라즈베리파이 등록. 멱등 — 이미 있으면 기존 deviceNo 를 그대로 돌려준다.
    @Transactional
    public DeviceResponse createDevice(Long memberNo) {
        Long existing = raspMapper.getDeviceNoByMemberNo(memberNo);
        if (existing != null) {
            return new DeviceResponse(existing);
        }
        Map<String, Object> param = new HashMap<>();
        param.put("memberNo", memberNo);
        raspMapper.insertDevice(param);
        Long deviceNo = ((Number) param.get("deviceNo")).longValue();
        return new DeviceResponse(deviceNo);
        // 동시성: 같은 회원이 정확히 동시에 두 번 등록하면 두 번째 INSERT 가 UK_DEVICE_MEMBER 를 위반한다.
        // DataIntegrityViolationException 핸들러가 없어 현재는 500 으로 나가지만, 사람이 1회 호출하는
        // 프로비저닝 시나리오라 감수한다. (막으려면 GlobalExceptionHandler 에 핸들러 추가 — 공용 코드 승인 필요)
    }

    // 내 라즈베리파이 번호 조회. 등록 안 했으면 404 (걸음 0건과 구분 — 프론트가 "먼저 등록해 주세요" 안내).
    @Transactional(readOnly = true)
    public DeviceResponse getDevice(Long memberNo) {
        return new DeviceResponse(getDeviceNoOrThrow(memberNo));
    }

    // 라즈베리파이가 보고한 그날 누적 걸음. 보고마다 새 행 INSERT (UPDATE 안 함).
    // deviceNo 는 요청 body 값이라(permitAll) 존재 검증 먼저 — 없으면 404.
    @Transactional
    public void createStepLog(StepLogCreateRequest request) {
        if (!raspMapper.existsByDeviceNo(request.deviceNo())) {
            throw new CustomException(ErrorCode.DEVICE_NOT_FOUND);
        }
        raspMapper.insertStepLog(request.deviceNo(), request.todaySteps());
    }

    // 오늘 인트라데이 곡선 (자정 리셋). 미등록 404, 데이터 없으면 빈 points.
    @Transactional(readOnly = true)
    public TodayStepListResponse getTodaySteps(Long memberNo) {
        Long deviceNo = getDeviceNoOrThrow(memberNo);
        return TodayStepListResponse.of(deviceNo, raspMapper.getTodayStepPoints(deviceNo));
    }

    // 지난 7일(오늘 포함) 일자별 총 걸음. 미등록 404, 데이터 없으면 빈 days.
    @Transactional(readOnly = true)
    public WeeklyStepListResponse getWeekSteps(Long memberNo) {
        Long deviceNo = getDeviceNoOrThrow(memberNo);
        return WeeklyStepListResponse.of(deviceNo, raspMapper.getWeeklyStepCounts(deviceNo));
    }

    // memberNo 의 deviceNo. 미등록이면 DEVICE_NOT_FOUND (device 조회·걸음 조회 공통 규칙).
    private Long getDeviceNoOrThrow(Long memberNo) {
        Long deviceNo = raspMapper.getDeviceNoByMemberNo(memberNo);
        if (deviceNo == null) {
            throw new CustomException(ErrorCode.DEVICE_NOT_FOUND);
        }
        return deviceNo;
    }
}
