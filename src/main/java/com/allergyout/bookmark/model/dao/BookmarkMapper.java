package com.allergyout.bookmark.model.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BookmarkMapper {

    // 파라미터 2개 이상은 @Param 필수: Eclipse(bin/) 실행 시 -parameters 가 꺼져 있어
    // @Param 없으면 MyBatis 가 #{memberNo} 등을 못 찾고 arg0/arg1 만 인식함.
    boolean isDuplicateBookmark(@Param("memberNo") Long memberNo, @Param("recipeNo") Long recipeNo);

    void insertBookmark(@Param("memberNo") Long memberNo, @Param("recipeNo") Long recipeNo);
}
