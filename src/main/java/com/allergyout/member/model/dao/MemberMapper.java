package com.allergyout.member.model.dao;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

import com.allergyout.member.model.vo.Member;

@Mapper
public interface MemberMapper {

    // --- auth 담당 (이번 작업에서 미변경) ---
    Optional<Member> findByMemberId(String memberId);
    boolean existsByMemberId(String memberId);
    void insert(Member member);

    // --- member 마이페이지 (이번 작업) ---
    // 파라미터명 바인딩(#{memberNo} 등)은 Spring Boot 플러그인이 -parameters를 켜주므로 @Param 없이 동작
    Member getMember(Long memberNo);

    void updateMemberName(Long memberNo, String memberName);

    boolean existsByEmailExcludingSelf(String email, Long memberNo);
    void updateMemberEmail(Long memberNo, String email);

    boolean existsByPhoneExcludingSelf(String phone, Long memberNo);
    void updateMemberPhone(Long memberNo, String phone);

    void updateMemberPwd(Long memberNo, String memberPwd);

    void updateMemberImgPath(Long memberNo, String memberImgPath);
}
