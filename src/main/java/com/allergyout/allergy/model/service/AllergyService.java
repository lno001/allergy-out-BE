package com.allergyout.allergy.model.service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.allergyout.allergy.model.dao.AllergyMapper;
import com.allergyout.allergy.model.dto.AllergyResponse;
import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;
import com.allergyout.member.model.dao.MemberMapper;
import com.allergyout.member.model.vo.Member;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AllergyService {

    private static final int MATERIAL_NAME_MAX_LENGTH = 30; // MEMBER_ALLERGY.MATERIAL_NAME NV(30)
    private static final int ALLERGY_LIST_MAX_SIZE = 100; // 회원 1명당 등록 가능한 알러지 항목 최대 개수

    private final AllergyMapper allergyMapper;
    private final MemberMapper memberMapper; // 회원 존재 확인용 - member 담당 조회 메소드 재사용

    @Transactional(readOnly = true)
    public AllergyResponse getAllergyList(Long memberNo) {
        getMemberByNo(memberNo);
        List<String> allergyList = allergyMapper.getAllergyList(memberNo);
        return AllergyResponse.from(allergyList);
    }

    @Transactional
    public AllergyResponse updateAllergyList(Long memberNo, List<String> allergyList) {
        getMemberByNo(memberNo);
        validateAllergyList(allergyList);
        allergyMapper.deleteAllergyList(memberNo);
        allergyList.forEach(materialName -> allergyMapper.insertAllergy(memberNo, materialName));
        return AllergyResponse.from(allergyList);
    }

    private Member getMemberByNo(Long memberNo) {
        Member member = memberMapper.getMember(memberNo);
        if (member == null) {
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return member;
    }

    private void validateAllergyList(List<String> allergyList) {
        if (allergyList.size() > ALLERGY_LIST_MAX_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, Map.of(
                    "allergyList", "알러지 항목은 최대 " + ALLERGY_LIST_MAX_SIZE + "개까지 등록할 수 있습니다."));
        }
        Map<String, String> details = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < allergyList.size(); i++) {
            String materialName = allergyList.get(i);
            String field = "allergyList[" + i + "]";
            if (materialName == null || materialName.isBlank()) {
                details.put(field, "알러지 항목은 비어있을 수 없습니다.");
            } else if (materialName.length() > MATERIAL_NAME_MAX_LENGTH) {
                details.put(field, "알러지 항목은 각각 30자 이내로 입력해주세요.");
            } else if (!seen.add(materialName)) {
                details.put(field, "중복된 알러지 항목입니다.");
            }
        }
        if (!details.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, details);
        }
    }
}
