package com.allergyout.allergy.model.dto;

import java.util.List;

public record AllergyResponse(List<String> allergyList) {
    public static AllergyResponse from(List<String> allergyList) {
        return new AllergyResponse(allergyList);
    }
}
