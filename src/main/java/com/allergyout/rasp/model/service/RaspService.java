package com.allergyout.rasp.model.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;
import com.allergyout.rasp.model.dao.RaspMapper;
import com.allergyout.rasp.model.dto.DeviceResponse;

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
}
