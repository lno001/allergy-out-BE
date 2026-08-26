package com.allergyout.global.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.allergyout.member.model.vo.Member;

import lombok.Getter;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long memberNo;
    private final String memberId;
    private final String memberPwd;
    private final String memberName;
    private final String role;
    private final boolean withdrawn; // DEL_YN = 'Y'

    public CustomUserDetails(Member member) {
        this.memberNo = member.getMemberNo();
        this.memberId = member.getMemberId();
        this.memberPwd = member.getMemberPwd();
        this.memberName = member.getMemberName();
        this.role = member.getRole();
        this.withdrawn = "Y".equals(member.getDelYn());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() {
        return memberPwd;
    }

    @Override
    public String getUsername() {
        return memberId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !withdrawn;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return !withdrawn;
    }
}
