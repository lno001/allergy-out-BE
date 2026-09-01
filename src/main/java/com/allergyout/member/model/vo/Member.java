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

    private final Long memberNo;      // MEMBER_NO (PK, IDENTITY)
    private final String memberId;    // MEMBER_ID NVARCHAR2(20) (로그인 아이디)
    private final String memberPwd;   // MEMBER_PWD VARCHAR2(200) (암호화된 비밀번호)
    private final String memberName;  // MEMBER_NAME NVARCHAR2(30)
    private final String phone;       // PHONE VARCHAR2(20)
    private final String email;       // EMAIL VARCHAR2(50)
    private final String memberImg;   // MEMBER_IMG VARCHAR2(300) NULL (업로드 원본 파일명)
    private final String role;        // ROLE VARCHAR2(10) DEFAULT 'ROLE_USER'
    private final LocalDateTime createDate; // CREATE_DATE DATE DEFAULT SYSDATE
    private final String delYn;       // DEL_YN CHAR(1) DEFAULT 'N' (Y: 탈퇴, N: 정상)
    private final String memberImgPath;    // MEMBER_IMG_PATH VARCHAR2(300) NULL (S3 버킷 링크)
}
