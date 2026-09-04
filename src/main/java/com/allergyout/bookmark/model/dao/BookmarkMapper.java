package com.allergyout.bookmark.model.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.allergyout.bookmark.model.dto.BookmarkListItem;

@Mapper
public interface BookmarkMapper {

    // 파라미터 2개 이상은 @Param 필수: Eclipse(bin/) 실행 시 -parameters 가 꺼져 있어
    // @Param 없으면 MyBatis 가 #{memberNo} 등을 못 찾고 arg0/arg1 만 인식함.
    boolean isDuplicateBookmark(@Param("memberNo") Long memberNo, @Param("recipeNo") Long recipeNo);

    void insertBookmark(@Param("memberNo") Long memberNo, @Param("recipeNo") Long recipeNo);

    // 내 즐겨찾기 목록 — 북마크한 날짜 최신순, OFFSET 페이징. 삭제된 레시피(RECIPES.DEL_YN='Y') 제외.
    List<BookmarkListItem> getBookmarkList(@Param("offset") int offset,
                                          @Param("size") int size,
                                          @Param("memberNo") Long memberNo);

    int countBookmarkList(@Param("memberNo") Long memberNo);

    // 내 즐겨찾기 1건 삭제 (복합 PK). 삭제된 행 수 반환 → 0 이면 대상 없음.
    int deleteBookmark(@Param("memberNo") Long memberNo, @Param("recipeNo") Long recipeNo);
}
