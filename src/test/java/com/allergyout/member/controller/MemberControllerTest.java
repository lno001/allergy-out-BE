package com.allergyout.member.controller;

import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.allergyout.global.security.CookieUtil;
import com.allergyout.global.security.CustomUserDetails;
import com.allergyout.global.security.JwtUtil;
import com.allergyout.member.model.dto.MemberAllergyResponse;
import com.allergyout.member.model.service.MemberService;
import com.allergyout.member.model.vo.Member;

// SecurityConfig가 이 슬라이스에도 로드돼 JwtFilter 빈 생성에 JwtUtil/CookieUtil/UserDetailsService가 필요 (mock으로 대체, 실제 DB/JWT 시크릿 불필요).
// addFilters=false로 필터 체인 자체는 끄고, @AuthenticationPrincipal이 읽는 SecurityContextHolder는 테스트에서 직접 채운다.
@WebMvcTest(controllers = MemberController.class)
@AutoConfigureMockMvc(addFilters = false)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CookieUtil cookieUtil;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private CustomUserDetails mockUser() {
        Member member = Member.builder()
                .memberNo(1L)
                .memberId("tester")
                .memberPwd("encoded")
                .memberName("테스터")
                .phone("01000000000")
                .email("tester@test.com")
                .memberImg(null)
                .role("ROLE_USER")
                .createDate(null)
                .delYn("N")
                .build();
        return new CustomUserDetails(member);
    }

    private void authenticateAsMockUser() {
        CustomUserDetails user = mockUser();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test
    void getAllergyList_인증된사용자_200과알러지목록반환() throws Exception {
        authenticateAsMockUser();
        when(memberService.getAllergyList(1L))
                .thenReturn(MemberAllergyResponse.from(List.of("땅콩", "우유", "갑각류")));

        mockMvc.perform(get("/api/members/allergy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("알러지 정보 조회 성공"))
                .andExpect(jsonPath("$.data.allergyList", contains("땅콩", "우유", "갑각류")));
    }

    @Test
    void getAllergyList_알러지없으면_빈리스트응답() throws Exception {
        authenticateAsMockUser();
        when(memberService.getAllergyList(1L))
                .thenReturn(MemberAllergyResponse.from(List.of()));

        mockMvc.perform(get("/api/members/allergy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allergyList").isEmpty());
    }
}
