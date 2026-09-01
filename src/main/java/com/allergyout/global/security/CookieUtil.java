package com.allergyout.global.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CookieUtil {

    public static final String REFRESH_COOKIE = "refreshToken";

    private final long refreshMaxAgeSeconds;
    private final boolean secure;

    public CookieUtil(
            @Value("${jwt.refresh-expiration}") long refreshExpirationMs,
            @Value("${jwt.cookie.secure:false}") boolean secure) {
        this.refreshMaxAgeSeconds = refreshExpirationMs / 1000;
        this.secure = secure;
    }
    
    public String getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public void addRefreshToken(HttpServletResponse response, String token) {
        addCookie(response, REFRESH_COOKIE, token, "/api/auth", "Lax", refreshMaxAgeSeconds);
    }

    public void deleteAuthCookies(HttpServletResponse response) {
        addCookie(response, REFRESH_COOKIE, "", "/api/auth", "Lax", 0);
    }

    private void addCookie(HttpServletResponse response, String name, String value,
                           String path, String sameSite, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)   
                .secure(secure)   
                .sameSite(sameSite) 
                .path(path)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}