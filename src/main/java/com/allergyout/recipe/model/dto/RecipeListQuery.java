package com.allergyout.recipe.model.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * GET /api/recipes 목록 조회 조건. Controller 가 @ModelAttribute 로 query string 을 이 record 에 바인딩한다
 * (등록/수정 폼의 RecipeCreateRequest 와 같은 방식 — 쿼리 key = 필드명 카멜케이스 그대로).
 *
 * 목록·검색·필터·정렬이 이 엔드포인트 하나로 통합됐다. 모든 조건은 선택 — 미전송이면 그 조건이 빠진다.
 *  - keyword          : 제목(RECIPE_TITLE) 부분일치. 공백뿐이면 Service 가 null 처리(전체 조회)
 *  - excludeMaterials : 그 재료가 하나라도 든 레시피 제외. 반복 파라미터(?e=계란&e=우유)·콤마(?e=계란,우유) 둘 다 바인딩
 *  - recipeType       : 6종 단일·완전일치 (미전송=전체). enum 밖 값 → @Pattern 400
 *  - cookingMethod    : 6종 단일·완전일치 (미전송=전체). enum 밖 값 → @Pattern 400
 *  - sort             : latest(기본) | popular. 그 외 값은 Service 가 latest 로 폴백 → @Pattern 안 붙임
 *  - applyMyAllergy   : true(기본) | false. 회원 알러지 자동 제외 on/off. 그 외 값(빈값·1·yes) → @Pattern 400
 *
 * memberNo 는 여기 없다 — 인증 principal 에서 따로 온다(요청 파라미터가 아니라서 분리).
 * 형식 검증(page·size 범위, enum, applyMyAllergy 값)은 전부 이 record 의 제약이 담당한다(@Valid → 400).
 */
public record RecipeListQuery(

        @PositiveOrZero                                        // page < 0 → 400
        Integer page,

        @Min(1) @Max(50)                                       // 1 ≤ size ≤ 50, 벗어나면 400
        Integer size,

        String keyword,

        List<String> excludeMaterials,

        @Pattern(regexp = "밥|국&찌개|반찬|일품|후식|기타")      // RECIPES.RECIPE_TYPE 6값 고정. null(미전송)은 통과
        String recipeType,

        @Pattern(regexp = "굽기|튀기기|볶기|찌기|끓이기|기타")   // RECIPES.COOKING_METHOD 6값 고정. null 은 통과
        String cookingMethod,

        String sort,

        @Pattern(regexp = "true|false")                        // 그 외 값 → 400 (계약서). null 은 통과 → Service 가 true 로 간주
        String applyMyAllergy
) {
    // query string 은 @RequestParam defaultValue 를 못 쓰므로, 미전송 시 기본값을 여기서 채운다.
    // (제약 검증은 이 압축 생성자가 돈 뒤의 값으로 평가되므로 page=0 / size=20 은 항상 통과)
    public RecipeListQuery {
        if (page == null) {
            page = 0;
        }
        if (size == null) {
            size = 20;
        }
    }
}
