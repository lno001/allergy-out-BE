package com.allergyout.recipe.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.allergyout.global.common.ApiResponse;
import com.allergyout.global.security.CustomUserDetails;
import com.allergyout.recipe.model.dto.RecipeCreateRequest;
import com.allergyout.recipe.model.service.RecipeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    // POST /api/recipes  (multipart/form-data) — 인증 필요, 작성자 = 로그인한 memberNo
    // 텍스트/리스트 필드와 스텝 이미지(STEP_LIST[i].STEP_IMG)는 @ModelAttribute DTO로,
    // 대표 이미지(RECIPE_MAIN_IMG)만 @RequestParam으로 분리해서 받는다.
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createRecipe(
            												@Valid @ModelAttribute RecipeCreateRequest request,
            												@RequestParam("RECIPE_MAIN_IMG") MultipartFile mainImg,
            												@AuthenticationPrincipal CustomUserDetails userDetails) {

        recipeService.createRecipe(request, mainImg, userDetails.getMemberNo());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("레시피 등록 성공했습니다.", null));
    }
}
