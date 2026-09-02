package com.allergyout.bookmark.model.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// MyBatis로 매핑되는 VO - BOOKMARK 테이블과 1:1, 불변(setter 없음).
// 복합 PK (MEMBER_NO, RECIPE_NO), 대리키 없음.
@Getter
@Builder
@AllArgsConstructor
public class Bookmark {

    private final Long memberNo;            // MEMBER_NO (PK, FK → MEMBER)
    private final Long recipeNo;            // RECIPE_NO (PK, FK → RECIPES)
    private final LocalDateTime createDate; // CREATE_DATE (DB default SYSDATE)
}
