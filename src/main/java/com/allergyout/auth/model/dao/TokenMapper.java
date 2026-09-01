package com.allergyout.auth.model.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TokenMapper {

    void insertToken(@Param("memberNo") Long memberNo,
            @Param("token") String token,
            @Param("expiration") Long expiration);

    int countValidToken(@Param("token") String token, @Param("now") Long now);

    void deleteByToken(String token);

    void deleteByMemberNo(Long memberNo);
}