package com.allergyout.recipe.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
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
import com.allergyout.recipe.model.dto.RecipeListQuery;
import com.allergyout.recipe.model.dto.RecipeListResponse;
import com.allergyout.recipe.model.dto.RecipeRecommendQuery;
import com.allergyout.recipe.model.dto.RecipeRecommendResponse;
import com.allergyout.recipe.model.dto.RecipeUpdateRequest;
import com.allergyout.recipe.model.service.RecipeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    // GET /api/recipes — 목록·검색·필터·정렬 통합 엔드포인트. 인증 선택(비회원도 조회 가능).
    //  page·size·keyword·excludeMaterials·recipeType·cookingMethod·sort·applyMyAllergy 를 RecipeListQuery 로 바인딩
    //  (등록/수정 폼과 같은 @ModelAttribute 방식 — 쿼리 key = 필드명 카멜케이스).
    //  로그인 + applyMyAllergy != false 면 그 회원 알러지 재료가 든 레시피는 자동 제외.
    //  형식 검증(page·size 범위, recipeType/cookingMethod enum, applyMyAllergy 값)은 @Valid 가 400 으로 처리.
    @GetMapping
    public ResponseEntity<ApiResponse<RecipeListResponse>> getRecipeList(
            @Valid @ModelAttribute RecipeListQuery query,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long memberNo = (userDetails != null) ? userDetails.getMemberNo() : null;
        RecipeListResponse data = recipeService.getRecipeList(query, memberNo);

        return ResponseEntity.ok(ApiResponse.success("레시피 목록 조회 성공했습니다.", data));
    }
    
    // GET /api/recipes/recommend — 오늘의 추천 최대 3개. 인증 선택(목록과 동일).
    // date 는 필수(YYYY-MM-DD). 나머지 필터는 RecipeRecommendQuery @Valid.
    @GetMapping("/recommend")
    public ResponseEntity<ApiResponse<RecipeRecommendResponse>> getRecommendRecipes(
            @Valid @ModelAttribute RecipeRecommendQuery query,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long memberNo = (userDetails != null) ? userDetails.getMemberNo() : null;
        RecipeRecommendResponse data = recipeService.getRecommendRecipes(query, memberNo);

        return ResponseEntity.ok(ApiResponse.success("오늘의 추천 레시피 조회 성공했습니다.", data));
    }

    // GET /api/recipes/recommend/calorie?totalCalories=2100 — 인증 필요.
    // 오늘 하루 목표 칼로리(FE 계산) 기준 추천. 끼니당 목표(totalCalories/3)에 CALORIE 가 가장 가까운 3개,
    // 회원 알러지 재료가 든 레시피는 제외. 후보 없으면 빈 리스트.
    // 날짜기반 "오늘의 추천"(GET /api/recipes/recommend)과 별개 엔드포인트다.
    // totalCalories 누락/범위밖은 Service 에서 400(INVALID_INPUT_VALUE), 숫자 아님은 400(TypeMismatch).
    @GetMapping("/recommend/calorie")
    public ResponseEntity<ApiResponse<RecipeRecommendResponse>> getCalorieRecommendRecipes(
            @RequestParam(name = "totalCalories", required = false) Double totalCalories,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        RecipeRecommendResponse data =
                recipeService.getCalorieRecommendRecipes(userDetails.getMemberNo(), totalCalories);
        return ResponseEntity.ok(ApiResponse.success("칼로리 기반 추천 레시피를 조회했습니다.", data));
    }

    // GET /api/recipes/me — 인증 필요. 로그인 회원이 작성한 레시피 최신순 페이징.
    // page·size 는 raw @RequestParam (형식·기본값만 보장), 값 범위 검증은 Service. 리터럴 "/me" 를 "/{recipeNo}" 앞에 둔다.
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<RecipeListResponse>> getMyRecipeList(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        RecipeListResponse data = recipeService.getMyRecipeList(userDetails.getMemberNo(), page, size);
        return ResponseEntity.ok(ApiResponse.success("내 레시피 목록을 조회했습니다.", data));
    }

    // GET /api/recipes/{recipeNo} — 인증 선택 (permitAll 유지, 토큰 없어도 200). 레시피 1건 상세 (recipe + 재료 + 조리 단계).
    // 토큰이 있으면 그 회원의 즐겨찾기 여부(isBookmarked)를 판정, 없으면 false. 비로그인이면 userDetails = null.
    // recipeNo 가 숫자가 아니면 MethodArgumentTypeMismatchException → GlobalExceptionHandler 가 400.
    @GetMapping("/{recipeNo}")
    public ResponseEntity<ApiResponse<RecipeDetailResponse>> getRecipe(
            @PathVariable("recipeNo") Long recipeNo,  // 이름 명시 — Eclipse는 -parameters 없이 컴파일해서 필수
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long memberNo = (userDetails != null) ? userDetails.getMemberNo() : null;
        RecipeDetailResponse data = recipeService.getRecipe(recipeNo, memberNo);
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

    // PATCH /api/recipes/{recipeNo}  (multipart/form-data) — 인증 필요, 작성자 본인만 수정 가능.
    // 재료·단계 리스트와 단계 이미지(stepList[i].stepImg)는 @ModelAttribute DTO로,
    // 대표 이미지(recipeMainImg)만 @RequestParam으로 분리. 변경 안 했으면 미전송 → required = false.
    @PatchMapping("/{recipeNo}")
    public ResponseEntity<ApiResponse<Void>> updateRecipe(
            @PathVariable("recipeNo") Long recipeNo,   // 이름 명시 — Eclipse는 -parameters 없이 컴파일
            @Valid @ModelAttribute RecipeUpdateRequest request,
            @RequestParam(value = "recipeMainImg", required = false) MultipartFile mainImg,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        recipeService.updateRecipe(recipeNo, request, mainImg, userDetails.getMemberNo());

        return ResponseEntity.ok(ApiResponse.success("레시피 수정 성공했습니다.", null));
    }

    // DELETE /api/recipes/{recipeNo} — 인증 필요, 작성자 본인만. 소프트 삭제 (RECIPES.DEL_YN='Y').
    @DeleteMapping("/{recipeNo}")
    public ResponseEntity<ApiResponse<Void>> deleteRecipe(
            @PathVariable("recipeNo") Long recipeNo,   // 이름 명시 — Eclipse는 -parameters 없이 컴파일
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        recipeService.deleteRecipe(recipeNo, userDetails.getMemberNo());

        return ResponseEntity.ok(ApiResponse.success("레시피 삭제 성공했습니다.", null));
    }
}
