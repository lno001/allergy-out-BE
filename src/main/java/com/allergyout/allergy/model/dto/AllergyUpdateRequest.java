package com.allergyout.allergy.model.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record AllergyUpdateRequest(
        @NotNull(message = "알러지 목록을 입력해주세요.")
        List<String> allergyList
) {}
