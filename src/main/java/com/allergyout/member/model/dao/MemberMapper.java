package com.allergyout.member.model.dao;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.allergyout.member.model.vo.Member;

@Mapper
public interface MemberMapper {

    // --- member 마이페이지 ---
    // 파라미터 2개 이상인 메서드는 @Param 필수: Eclipse(bin/) 실행 시 -parameters 가 꺼져 있어
    // @Param 없으면 MyBatis 가 #{email} 등을 못 찾고 arg0/arg1 만 인식함.
    Member getMember(Long memberNo);

    void updateMemberName(@Param("memberNo") Long memberNo, @Param("memberName") String memberName);

    boolean existsByEmailExcludingSelf(@Param("emailHash") String emailHash, @Param("memberNo") Long memberNo);
    void updateMemberEmail(@Param("memberNo") Long memberNo,
            @Param("email") String email,
            @Param("emailHash") String emailHash);

    boolean existsByPhoneExcludingSelf(@Param("phoneHash") String phoneHash, @Param("memberNo") Long memberNo);
    void updateMemberPhone(@Param("memberNo") Long memberNo,
            @Param("phone") String phone,
            @Param("phoneHash") String phoneHash);

    void updateMemberPwd(@Param("memberNo") Long memberNo, @Param("memberPwd") String memberPwd);

    // 프로필 사진: MEMBER_IMG = 업로드 원본 파일명, MEMBER_IMG_PATH = S3 URL. 삭제 시 둘 다 null.
    void updateMemberImg(@Param("memberNo") Long memberNo,
            @Param("memberImg") String memberImg,
            @Param("memberImgPath") String memberImgPath);

    void updateMemberDelYn(Long memberNo); // 회원 탈퇴 - 소프트 삭제(DEL_YN='Y')

    // --- auth (로그인/회원가입) ---
    void insertMember(@Param("m") Member m,
            @Param("emailHash") String emailHash,
            @Param("phoneHash") String phoneHash);

    Optional<Member> findByMemberId(String memberId);

    boolean isDuplicateMemberId(String memberId);

    boolean isDuplicateEmail(@Param("emailHash") String emailHash);

    boolean isDuplicatePhone(@Param("phoneHash") String phoneHash);
}
