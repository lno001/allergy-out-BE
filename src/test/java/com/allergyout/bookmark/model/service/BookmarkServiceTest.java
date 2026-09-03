package com.allergyout.bookmark.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.allergyout.bookmark.model.dao.BookmarkMapper;
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
    @DisplayName("이미 즐겨찾기한 레시피면 DUPLICATE_VALUE + data{recipeNo}, insert 미호출")
    void createBookmark_duplicated() {
        when(bookmarkMapper.isDuplicateBookmark(MEMBER_NO, RECIPE_NO)).thenReturn(true);

        assertThatThrownBy(() -> bookmarkService.createBookmark(MEMBER_NO, RECIPE_NO))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> {
                    CustomException ce = (CustomException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_VALUE);
                    assertThat(ce.getDetails()).containsExactly(entry("recipeNo", "이미 즐겨찾기한 레시피입니다."));
                });
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
}
