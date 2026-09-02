package com.allergyout.allergy.model.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AllergyMapper {

    List<String> getAllergyList(Long memberNo); // MEMBER_ALLERGY.MATERIAL_NAME 목록

    void deleteAllergyList(Long memberNo); // 전체 교체 전 기존 행 삭제

    void insertAllergy(@Param("memberNo") Long memberNo, @Param("materialName") String materialName);
}
