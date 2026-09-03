package com.allergyout.bookmark.model.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.allergyout.bookmark.model.dao.BookmarkMapper;
import com.allergyout.bookmark.model.dto.BookmarkListItem;
import com.allergyout.bookmark.model.dto.BookmarkListResponse;
import com.allergyout.global.common.PageInfo;
import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;
import com.allergyout.recipe.model.service.RecipeService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkMapper bookmarkMapper;
    private final RecipeService recipeService;

    @Transactional
    public void createBookmark(Long memberNo, Long recipeNo) {
        // 레시피 존재·활성(DEL_YN='N') 검증 → 없으면 RECIPE_NOT_FOUND. 통과하면 중복 검사(409).
        recipeService.validateRecipeExists(recipeNo);
        if (bookmarkMapper.isDuplicateBookmark(memberNo, recipeNo)) {
            throw new CustomException(ErrorCode.DUPLICATE_VALUE, Map.of("recipeNo", "이미 즐겨찾기한 레시피입니다."));
        }
        bookmarkMapper.insertBookmark(memberNo, recipeNo);
    }

    // 내 즐겨찾기 목록 — 북마크한 날짜 최신순, OFFSET 페이징. data = { recipes, pageInfo }.
    // 삭제된 레시피는 매퍼에서 제외(RECIPES.DEL_YN='N').
    @Transactional(readOnly = true)
    public BookmarkListResponse getBookmarkList(Long memberNo, int page, int size) {
        validatePageParams(page, size);

        int totalElements = bookmarkMapper.countBookmarkList(memberNo);
        PageInfo pageInfo = new PageInfo(page, size);
        pageInfo.calculateTotalPage(totalElements);

        // 마지막 페이지 초과 조회 = 잘못된 요청 → 400 (빈 리스트 아님). page 0 또는 결과 0건은 정상.
        if (totalElements > 0 && page >= pageInfo.getTotalPages()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    Map.of("page", "존재하지 않는 페이지입니다."));
        }

        List<BookmarkListItem> recipes = bookmarkMapper.getBookmarkList(pageInfo.getOffset(), size, memberNo);
        return new BookmarkListResponse(recipes, pageInfo);
    }

    // 형식(기본값·타입)은 Controller @RequestParam, 여기선 값 범위만 (page ≥ 0, 1 ≤ size ≤ 50). recipe 와 동일 로직.
    private void validatePageParams(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
