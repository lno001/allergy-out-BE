package com.allergyout.member.model.dao;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

import com.allergyout.member.model.vo.Member;

@Mapper
public interface MemberMapper {

	void insertMember(Member member);

	Optional<Member> findByMemberId(String memberId);

    boolean existsByMemberId(String memberId);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

}
