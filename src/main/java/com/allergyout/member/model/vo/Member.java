package com.allergyout.member.model.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// MyBatis로 매핑되는 VO - ERD의 MEMBER 테이블과 1:1 매핑, 불변(setter 없음)
// 생성자를 AllArgsConstructor 하나만 둬야 MyBatis가 컬럼 순서 기준으로 이 생성자를 자동으로 씀
@Getter
@Builder
@AllArgsConstructor
public class Member {

    private final Long memberNo;      // MEMBER_NO (PK)
    private final String memberId;    // MEMBER_ID (로그인 아이디)
    private final String memberPwd;   // MEMBER_PWD (암호화된 비밀번호)
    private final String memberName;  // MEMBER_NAME
    private final String phone;       // PHONE
    private final String email;       // EMAIL
    private final String memberImg;   // MEMBER_IMG (프로필 사진 경로)
    private final String role;        // ROLE (기본값 ROLE_USER, DB default)
    private final LocalDateTime createDate; // CREATE_DATE (DB default SYSDATE)
    private final String delYn;       // DEL_YN (Y: 탈퇴, N: 정상, DB default 'N')
}
