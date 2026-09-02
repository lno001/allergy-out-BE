package com.allergyout.recipe.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.allergyout.global.common.ApiResponse;
import com.allergyout.global.security.CustomUserDetails;
import com.allergyout.recipe.model.dto.RecipeCreateRequest;
import com.allergyout.recipe.model.dto.RecipeDetailResponse;
import com.allergyout.recipe.model.dto.RecipeListResponse;
import com.allergyout.recipe.model.service.RecipeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    // GET /api/recipes?page=0&size=20 — 인증 선택.
    // 비회원도 조회 가능. 로그인이면 그 회원 알러지 재료가 든 레시피는 자동 제외.
    @GetMapping
    public ResponseEntity<ApiResponse<RecipeListResponse>> getRecipeList(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long memberNo = (userDetails != null) ? userDetails.getMemberNo() : null;
        RecipeListResponse data = recipeService.getRecipeList(page, size, memberNo);

        return ResponseEntity.ok(ApiResponse.success("레시피 목록 조회 성공했습니다.", data));
    }

    // GET /api/recipes/{recipeNo} — 인증 없음. 레시피 1건 상세 (recipe + 재료 + 조리 단계).
    // recipeNo 가 숫자가 아니면 MethodArgumentTypeMismatchException → GlobalExceptionHandler 가 400.
    @GetMapping("/{recipeNo}")
    public ResponseEntity<ApiResponse<RecipeDetailResponse>> getRecipe(
            @PathVariable("recipeNo") Long recipeNo) {  // 이름 명시 — Eclipse는 -parameters 없이 컴파일해서 필수
        RecipeDetailResponse data = recipeService.getRecipe(recipeNo);
        return ResponseEntity.ok(ApiResponse.success("레시피 상세 조회 성공했습니다.", data));
    }

    // POST /api/recipes  (multipart/form-data) — 인증 필요, 작성자 = 로그인한 memberNo
    // 텍스트/리스트 필드와 스텝 이미지(stepList[i].stepImg)는 @ModelAttribute DTO로,
    // 대표 이미지(recipeMainImg)만 @RequestParam으로 분리해서 받는다.
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createRecipe(
            												@Valid @ModelAttribute RecipeCreateRequest request,
            												@RequestParam("recipeMainImg") MultipartFile mainImg,
            												@AuthenticationPrincipal CustomUserDetails userDetails) {

        recipeService.createRecipe(request, mainImg, userDetails.getMemberNo());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("레시피 등록 성공했습니다.", null));
    }
}
