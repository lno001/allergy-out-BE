package com.allergyout.recipe.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// MyBatis로 매핑되는 도메인 (JPA 엔티티 아님)
@Getter
@Builder
@AllArgsConstructor
public class Recipe {

    private final Long id;

}
