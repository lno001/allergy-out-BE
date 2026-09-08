package com.allergyout.rasp.model.dao;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.allergyout.rasp.model.dto.DayStep;
import com.allergyout.rasp.model.dto.StepPoint;

@Mapper
public interface RaspMapper {

    // 생성 PK(deviceNo)를 되받아야 해서 Map 파라미터 (RecipeMapper.insertRecipe 와 동일 이유 —
    // 불변 record 는 useGeneratedKeys 가 키를 써넣지 못함). 채워진 값은 param.get("deviceNo").
    void insertDevice(Map<String, Object> param);

    // 그 회원의 디바이스 번호 (1회원 1디바이스). 없으면 null — 멱등 등록 판정용.
    Long getDeviceNoByMemberNo(Long memberNo);

    // 걸음 저장 전 deviceNo 존재 검증용 (요청 body 값이라 신뢰 못 함).
    boolean existsByDeviceNo(Long deviceNo);

    // 보고마다 새 행 INSERT (UPDATE 안 함). PK·CREATE_DATE 는 IDENTITY / DB default.
    void insertStepLog(@Param("deviceNo") Long deviceNo,
                       @Param("todaySteps") Integer todaySteps);

    // 오늘(TRUNC(SYSDATE) 이후) 보고 기록 전부, 시각 오름차순.
    List<StepPoint> getStepPoints(Long deviceNo);

    // 오늘 포함 7일간 일자별 MAX(TODAY_STEPS). 데이터 없는 날은 행 없음.
    List<DayStep> getDayStepList(Long deviceNo);
}
