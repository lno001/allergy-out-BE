package com.allergyout.bookmark.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.allergyout.bookmark.model.dto.BookmarkCreateRequest;
import com.allergyout.bookmark.model.dto.BookmarkListResponse;
import com.allergyout.bookmark.model.service.BookmarkService;
import com.allergyout.global.common.ApiResponse;
import com.allergyout.global.security.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createBookmark(
            @Valid @RequestBody BookmarkCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        bookmarkService.createBookmark(user.getMemberNo(), request.recipeNo());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("즐겨찾기를 등록했습니다.", null));
    }

    // GET /api/bookmarks?page=0&size=20 — 인증 필요. 내 즐겨찾기 목록(북마크한 날짜 최신순).
    // page/size 형식·범위 밖이면 400, 마지막 페이지 초과 조회도 400.
    @GetMapping
    public ResponseEntity<ApiResponse<BookmarkListResponse>> getBookmarkList(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails user) {
        BookmarkListResponse data = bookmarkService.getBookmarkList(user.getMemberNo(), page, size);
        return ResponseEntity.ok(ApiResponse.success("즐겨찾기 목록을 조회했습니다.", data));
    }
}
