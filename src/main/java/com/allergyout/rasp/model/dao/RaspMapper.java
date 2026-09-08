package com.allergyout.rasp.model.dao;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RaspMapper {

    // 생성 PK(deviceNo)를 되받아야 해서 Map 파라미터 (RecipeMapper.insertRecipe 와 동일 이유 —
    // 불변 record/VO 는 useGeneratedKeys 가 키를 써넣지 못함). 채워진 값은 param.get("deviceNo").
    void insertDevice(Map<String, Object> param);

    // 그 회원의 디바이스 번호 (1회원 1디바이스). 없으면 null — 멱등 등록 판정용.
    Long getDeviceNoByMemberNo(Long memberNo);
}
