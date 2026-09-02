package com.allergyout.bookmark.model.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.allergyout.bookmark.model.dao.BookmarkMapper;
import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkMapper bookmarkMapper;

    @Transactional
    public void createBookmark(Long memberNo, Long recipeNo) {
        // TODO(recipe 담당): 레시피 존재·활성(DEL_YN='N') 검증. 메서드 나오면 아래 한 줄 활성화.
        //   recipeService.getRecipeByNo(recipeNo);   // 없으면 CustomException(ErrorCode.ENTITY_NOT_FOUND)
        //   → BookmarkService 에 RecipeService(또는 RecipeMapper) 주입 필요
        if (bookmarkMapper.isDuplicateBookmark(memberNo, recipeNo)) {
            throw new CustomException(ErrorCode.DUPLICATE_VALUE, Map.of("recipeNo", "이미 즐겨찾기한 레시피입니다."));
        }
        bookmarkMapper.insertBookmark(memberNo, recipeNo);
    }
}
