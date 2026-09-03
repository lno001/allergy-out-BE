package com.allergyout.bookmark.model.dto;

import java.util.List;

import com.allergyout.global.common.PageInfo;

// GET /api/bookmarks 응답의 data. { recipes: [...], pageInfo: { page, size, offset, totalElements, totalPages } }
// 래퍼 키 = "recipes" (RecipeListResponse 와 동일 — 프론트가 같은 목록 컴포넌트 재사용).
public record BookmarkListResponse(
        List<BookmarkListItem> recipes,
        PageInfo pageInfo
) {
}
