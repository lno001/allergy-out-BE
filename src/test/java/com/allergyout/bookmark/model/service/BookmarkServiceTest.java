package com.allergyout.bookmark.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.allergyout.bookmark.model.dao.BookmarkMapper;
import com.allergyout.bookmark.model.dto.BookmarkListItem;
import com.allergyout.bookmark.model.dto.BookmarkListResponse;
import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;
import com.allergyout.recipe.model.service.RecipeService;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @Mock
    private BookmarkMapper bookmarkMapper;

    @Mock
    private RecipeService recipeService;

    @InjectMocks
    private BookmarkService bookmarkService;

    private static final long MEMBER_NO = 1L;
    private static final long RECIPE_NO = 12L;

    @Test
    @DisplayName("중복이 아니면 즐겨찾기를 저장한다")
    void createBookmark_success() {
        when(bookmarkMapper.isDuplicateBookmark(MEMBER_NO, RECIPE_NO)).thenReturn(false);

        bookmarkService.createBookmark(MEMBER_NO, RECIPE_NO);

        verify(recipeService).validateRecipeExists(RECIPE_NO);
        verify(bookmarkMapper).insertBookmark(MEMBER_NO, RECIPE_NO);
    }

    @Test
    @DisplayName("이미 즐겨찾기한 레시피면 ALREADY_BOOKMARKED, insert 미호출")
    void createBookmark_duplicated() {
        when(bookmarkMapper.isDuplicateBookmark(MEMBER_NO, RECIPE_NO)).thenReturn(true);

        assertThatThrownBy(() -> bookmarkService.createBookmark(MEMBER_NO, RECIPE_NO))
                .isInstanceOfSatisfying(CustomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ALREADY_BOOKMARKED));
        verify(bookmarkMapper, never()).insertBookmark(any(), any());
    }

    @Test
    @DisplayName("존재하지 않는(또는 삭제된) 레시피면 RECIPE_NOT_FOUND, 중복검사·insert 미호출")
    void createBookmark_recipeNotFound() {
        doThrow(new CustomException(ErrorCode.RECIPE_NOT_FOUND))
                .when(recipeService).validateRecipeExists(RECIPE_NO);

        assertThatThrownBy(() -> bookmarkService.createBookmark(MEMBER_NO, RECIPE_NO))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RECIPE_NOT_FOUND));

        verify(bookmarkMapper, never()).isDuplicateBookmark(any(), any());
        verify(bookmarkMapper, never()).insertBookmark(any(), any());
    }

    private BookmarkListItem item(long recipeNo) {
        return new BookmarkListItem(recipeNo, "제목" + recipeNo, "img.jpg",
                "https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/3/img.jpg", "김민재",
                LocalDate.of(2026, 8, 18));
    }

    @Test
    @DisplayName("목록: 북마크가 있으면 recipes + pageInfo 를 조립해 반환한다")
    void getBookmarkList_success() {
        when(bookmarkMapper.countBookmarkList(MEMBER_NO)).thenReturn(2);
        when(bookmarkMapper.getBookmarkList(0, 20, MEMBER_NO)).thenReturn(List.of(item(11), item(12)));

        BookmarkListResponse res = bookmarkService.getBookmarkList(MEMBER_NO, 0, 20);

        assertThat(res.recipes()).hasSize(2);
        assertThat(res.pageInfo().getTotalElements()).isEqualTo(2);
        assertThat(res.pageInfo().getTotalPages()).isEqualTo(1);
        assertThat(res.pageInfo().getOffset()).isZero();
        verify(bookmarkMapper).getBookmarkList(0, 20, MEMBER_NO);
    }

    @Test
    @DisplayName("목록: 북마크 0개면 page 0 은 빈 리스트로 정상 반환(에러 아님)")
    void getBookmarkList_emptyFirstPage() {
        when(bookmarkMapper.countBookmarkList(MEMBER_NO)).thenReturn(0);
        when(bookmarkMapper.getBookmarkList(0, 20, MEMBER_NO)).thenReturn(List.of());

        BookmarkListResponse res = bookmarkService.getBookmarkList(MEMBER_NO, 0, 20);

        assertThat(res.recipes()).isEmpty();
        assertThat(res.pageInfo().getTotalElements()).isZero();
        assertThat(res.pageInfo().getTotalPages()).isZero();
    }

    @Test
    @DisplayName("목록: 마지막 페이지 초과 조회면 INVALID_INPUT_VALUE + data{page}, 목록 쿼리 미호출")
    void getBookmarkList_pageOverflow() {
        when(bookmarkMapper.countBookmarkList(MEMBER_NO)).thenReturn(5);

        assertThatThrownBy(() -> bookmarkService.getBookmarkList(MEMBER_NO, 1, 20))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                    assertThat(ce.getDetails()).containsExactly(entry("page", "존재하지 않는 페이지입니다."));
                });

        verify(bookmarkMapper, never()).getBookmarkList(anyInt(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("목록: page 가 음수면 INVALID_INPUT_VALUE + data{page}, count/목록 쿼리 미호출")
    void getBookmarkList_negativePage() {
        assertThatThrownBy(() -> bookmarkService.getBookmarkList(MEMBER_NO, -1, 20))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                    assertThat(ce.getDetails()).containsExactly(entry("page", "0 이상이어야 합니다."));
                });

        verify(bookmarkMapper, never()).countBookmarkList(any());
        verify(bookmarkMapper, never()).getBookmarkList(anyInt(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("목록: size 가 범위 밖(51)이면 INVALID_INPUT_VALUE + data{size}, count/목록 쿼리 미호출")
    void getBookmarkList_invalidSize() {
        assertThatThrownBy(() -> bookmarkService.getBookmarkList(MEMBER_NO, 0, 51))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                    assertThat(ce.getDetails()).containsExactly(entry("size", "1 이상 50 이하여야 합니다."));
                });

        verify(bookmarkMapper, never()).countBookmarkList(any());
        verify(bookmarkMapper, never()).getBookmarkList(anyInt(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("삭제: 삭제된 행이 1이면 정상 완료")
    void deleteBookmark_success() {
        when(bookmarkMapper.deleteBookmark(MEMBER_NO, RECIPE_NO)).thenReturn(1);

        bookmarkService.deleteBookmark(MEMBER_NO, RECIPE_NO);

        verify(bookmarkMapper).deleteBookmark(MEMBER_NO, RECIPE_NO);
    }

    @Test
    @DisplayName("삭제: 삭제 대상이 없으면(0행) BOOKMARK_NOT_FOUND")
    void deleteBookmark_notFound() {
        when(bookmarkMapper.deleteBookmark(MEMBER_NO, RECIPE_NO)).thenReturn(0);

        assertThatThrownBy(() -> bookmarkService.deleteBookmark(MEMBER_NO, RECIPE_NO))
                .isInstanceOfSatisfying(CustomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BOOKMARK_NOT_FOUND));
    }
}
