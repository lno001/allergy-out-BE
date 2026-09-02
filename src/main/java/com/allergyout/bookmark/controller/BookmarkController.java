package com.allergyout.bookmark.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.allergyout.bookmark.model.dto.BookmarkCreateRequest;
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
}
