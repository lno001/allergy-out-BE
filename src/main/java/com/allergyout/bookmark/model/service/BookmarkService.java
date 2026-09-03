package com.allergyout.bookmark.model.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.allergyout.bookmark.model.dao.BookmarkMapper;
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
}
