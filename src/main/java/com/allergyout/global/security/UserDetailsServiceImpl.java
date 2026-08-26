package com.allergyout.global.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.allergyout.member.model.dao.MemberMapper;
import com.allergyout.member.model.vo.Member;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final MemberMapper memberMapper;

    @Override
    public UserDetails loadUserByUsername(String memberId) throws UsernameNotFoundException {
        Member member = memberMapper.findByMemberId(memberId)
                .orElseThrow(() -> new UsernameNotFoundException("일치하는 회원이 없습니다: " + memberId));
        return new CustomUserDetails(member);
    }
}
