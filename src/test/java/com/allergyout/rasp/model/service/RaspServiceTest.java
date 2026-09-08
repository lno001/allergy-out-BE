package com.allergyout.rasp.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.allergyout.rasp.model.dao.RaspMapper;
import com.allergyout.rasp.model.dto.DeviceResponse;

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
}
