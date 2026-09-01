package com.allergyout.member.model.dto;

import java.util.List;

public record MemberAllergyResponse(List<String> allergyList) {
    public static MemberAllergyResponse from(List<String> allergyList) {
        return new MemberAllergyResponse(allergyList);
    }
}
