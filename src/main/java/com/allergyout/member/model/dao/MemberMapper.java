package com.allergyout.member.model.dao;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

import com.allergyout.member.model.vo.Member;

@Mapper
public interface MemberMapper {

    Optional<Member> findByMemberId(String memberId);

    boolean existsByMemberId(String memberId);

    void insert(Member member);
}
