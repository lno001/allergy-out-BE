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
            throw new CustomException(ErrorCode.ALREADY_BOOKMARKED);
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

    // 내 즐겨찾기 1건 삭제. memberNo 는 토큰에서 오므로 WHERE 로 스코프 → 소유자 별도 검증 없음.
    // 삭제된 행이 0이면 대상 없음 → 404.
    @Transactional
    public void deleteBookmark(Long memberNo, Long recipeNo) {
        int deleted = bookmarkMapper.deleteBookmark(memberNo, recipeNo);
        if (deleted == 0) {
            throw new CustomException(ErrorCode.BOOKMARK_NOT_FOUND);
        }
    }

    // 형식(기본값·타입)은 Controller @RequestParam, 여기선 값 범위만 (page ≥ 0, 1 ≤ size ≤ 50).
    // 어느 파라미터가 왜 틀렸는지 data 로 내려준다 (초과 페이지 응답과 형태 통일).
    private void validatePageParams(int page, int size) {
        if (page < 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, Map.of("page", "0 이상이어야 합니다."));
        }
        if (size < 1 || size > 50) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, Map.of("size", "1 이상 50 이하여야 합니다."));
        }
    }
}
