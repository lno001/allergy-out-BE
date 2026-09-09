package com.allergyout.recipe.model.service;

import java.net.URI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.allergyout.global.common.PageInfo;
import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;
import com.allergyout.recipe.model.dao.RecipeMapper;
import com.allergyout.recipe.model.dto.MaterialCreateRequest;
import com.allergyout.recipe.model.dto.MaterialUpdateRequest;
import com.allergyout.recipe.model.dto.RecipeCreateRequest;
import com.allergyout.recipe.model.dto.RecipeDetailItem;
import com.allergyout.recipe.model.dto.RecipeDetailResponse;
import com.allergyout.recipe.model.dto.RecipeListItem;
import com.allergyout.recipe.model.dto.RecipeListQuery;
import com.allergyout.recipe.model.dto.RecipeListResponse;
import com.allergyout.recipe.model.dto.RecipeCalorieRecommendResponse;
import com.allergyout.recipe.model.dto.RecipeRecommendItem;
import com.allergyout.recipe.model.dto.RecipeRecommendQuery;
import com.allergyout.recipe.model.dto.RecipeRecommendResponse;
import com.allergyout.recipe.model.dto.RecipeUpdateRequest;
import com.allergyout.recipe.model.dto.StepCreateRequest;
import com.allergyout.recipe.model.dto.StepUpdateRequest;
import com.allergyout.recipe.model.vo.Material;
import com.allergyout.recipe.model.vo.Recipe;
import com.allergyout.recipe.model.vo.RecipeStep;
import com.allergyout.s3.S3Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeService {

    // S3 디렉터리 (요청값 아님, 하드코딩 상수)
    private static final String DIR_RECIPE_MAIN = "recipes";
    private static final String DIR_RECIPE_STEP = "recipes/steps";

    // 칼로리 기반 추천(GET /api/recipes/recommend/calorie) 파라미터 — 요청값 아님, 서비스 내부 고정
    private static final int MEALS_PER_DAY = 3;          // 하루 목표 칼로리를 이 값으로 나눠 끼니당 목표
    private static final int RECOMMEND_COUNT = 3;        // 추천 레시피 개수
    private static final double TOTAL_CALORIES_MAX = 10000d; // 하루 목표 칼로리 상한 (방어)

    private final RecipeMapper recipeMapper;
    private final S3Service s3Service;

    @Transactional
    public void createRecipe(RecipeCreateRequest request, MultipartFile mainImg, Long memberNo) {
    	validateRecipeCreateRequest(request, mainImg);



        // S3는 DB 트랜잭션 밖이라, 흐름 중 예외가 나면 catch에서 올린 파일을 수동으로 지운다.git
        List<String> uploadedKeys = new ArrayList<>();
        try {
            // 1. 대표 이미지 업로드 (recipeNo가 아직 없어 memberNo로 키 구성)
            String mainImgUrl = s3Service.upload(mainImg, DIR_RECIPE_MAIN, memberNo);
            //extractS3Key 메소드 S3에서 파일을 지울때 URL이 필요한것이 아니라 URL에 붙어있는 key만 뽑아서 그 key들을 모아 S3파일 삭제 처리 해줌
            uploadedKeys.add(extractS3Key(mainImgUrl));

            // 2. RECIPES INSERT — 생성 PK를 되받아야 해서 Map 파라미터
            Map<String, Object> recipeParam = new HashMap<>();
            recipeParam.put("memberNo", memberNo);
            recipeParam.put("recipeTitle", request.recipeTitle());
            recipeParam.put("recipeInfo", request.recipeInfo());
            recipeParam.put("recipeMainImg", mainImg.getOriginalFilename());
            recipeParam.put("recipesImgPath",mainImgUrl);
            recipeParam.put("cookingMethod", request.cookingMethod());
            recipeParam.put("recipeType", request.recipeType());
            recipeParam.put("calorie", request.calorie());
            recipeParam.put("carbohydrate", request.carbohydrate());
            recipeParam.put("protein", request.protein());
            recipeParam.put("fat", request.fat());
            recipeParam.put("sodium", request.sodium());
            recipeParam.put("mainMaterial", blankToNull(request.mainMaterial()));
            recipeMapper.insertRecipe(recipeParam);
            Long recipeNo = ((Number) recipeParam.get("recipeNo")).longValue();

            // 3. 재료
            for (MaterialCreateRequest m : request.materialList()) {
                recipeMapper.insertMaterial(Material.builder()
                        .recipeNo(recipeNo)
                        .materialName(m.materialName())
                        .amount(m.amount())
                        .build());
            }

            // 4. 조리 단계 (스텝 이미지는 선택 — 없으면 STEP_IMG/STEP_IMG_PATH null)
            //    컬럼 규칙: STEP_IMG = 원본 파일명 / STEP_IMG_PATH = S3 URL (RECIPE_MAIN_IMG/RECIPES_IMG_PATH 와 동일)
            for (StepCreateRequest s : request.stepList()) {
                String stepImgUrl = null;
                String stepImgName = null;
                MultipartFile stepImg = s.stepImg();
                if (stepImg != null && !stepImg.isEmpty()) {
                    stepImgUrl = s3Service.upload(stepImg, DIR_RECIPE_STEP, recipeNo);
                    uploadedKeys.add(extractS3Key(stepImgUrl));
                    stepImgName = stepImg.getOriginalFilename();
                }
                recipeMapper.insertRecipeStep(RecipeStep.builder()
                        .recipeNo(recipeNo)
                        .stepInfo(s.stepInfo())
                        .stepImg(stepImgName)      // STEP_IMG = 원본 파일명
                        .stepOrder(s.stepOrder())
                        .stepImgPath(stepImgUrl)   // STEP_IMG_PATH = S3 URL
                        .build());
            }
        } catch (RuntimeException e) {
            deleteQuietly(uploadedKeys);
            throw e;
        }
    }

    // 목록 조회 (GET /api/recipes) — 목록·검색·필터·정렬 통합. data = { recipes, pageInfo }
    //  - keyword          : 제목 부분일치 (공백뿐이면 무시)
    //  - excludeMaterials : 제외 재료 (빈 리스트면 무시)
    //  - recipeType / cookingMethod : 완전일치 (미전송이면 무시). 6값 밖이면 @Pattern 이 이미 400 처리
    //  - sort             : "popular"(인기순) 만 인정, 그 외 전부 "latest"(최신순)
    //  - applyMyAllergy   : false 면 회원이라도 알러지 자동제외 끔 → 매퍼엔 memberNo=null 로 넘김
    //  형식 검증(page·size 범위, enum, applyMyAllergy 값)은 RecipeListQuery @Valid 가 담당한다.
    @Transactional(readOnly = true)
    public RecipeListResponse getRecipeList(RecipeListQuery query, Long memberNo) {
        // keyword 정규화: null·공백뿐이면 null(전체조회). trim 은 "양쪽 끝" 공백만 없앤다 —
        // 문자 사이 공백은 그대로 유지되므로 "된 장" 으로 검색하면 "된장" 은 안 잡힌다(명세: keyword = 단어 하나).
        String kw = normalizeKeyword(query.keyword());
        List<String> excludes = normalizeExcludeMaterials(query.excludeMaterials()); // null/blank 항목 제거, 비면 null
        String recipeType = blankToNull(query.recipeType());
        String cookingMethod = blankToNull(query.cookingMethod());
        String sort = normalizeSort(query.sort());                                   // "popular" 아니면 "latest"

        // applyMyAllergy: 미전송(null) 또는 "true" → 적용 / "false" → 미적용. (@Pattern 이 그 외 값은 이미 400)
        boolean applyMyAllergy = !"false".equals(query.applyMyAllergy());
        Long allergyMemberNo = (applyMyAllergy && memberNo != null) ? memberNo : null;
        // 즐겨찾기 표시는 알러지 제외 on/off 와 무관하게 항상 로그인 회원 기준 (비로그인이면 null → IS_BOOKMARKED 0)
        Long bookmarkMemberNo = memberNo;

        PageInfo pageInfo = new PageInfo(query.page(), query.size());
        int offset = pageInfo.getOffset();

        List<RecipeListItem> recipes = recipeMapper.getRecipeList(
                offset, query.size(), allergyMemberNo, bookmarkMemberNo, kw, excludes, recipeType, cookingMethod, sort);
        int totalElements = recipeMapper.countRecipeList(
                allergyMemberNo, kw, excludes, recipeType, cookingMethod);

        pageInfo.calculateTotalPage(totalElements);
        return new RecipeListResponse(recipes, pageInfo);
    }
    
    // 오늘의 추천 레시피
    @Transactional(readOnly = true)
    public RecipeRecommendResponse getRecommendRecipes(RecipeRecommendQuery query, Long memberNo) {
        String kw = normalizeKeyword(query.keyword());
        List<String> excludes = normalizeExcludeMaterials(query.excludeMaterials());
        String recipeType = blankToNull(query.recipeType());
        String cookingMethod = blankToNull(query.cookingMethod());

        boolean applyMyAllergy = !"false".equals(query.applyMyAllergy());
        Long allergyMemberNo = (applyMyAllergy && memberNo != null) ? memberNo : null;

        List<RecipeRecommendItem> recipes = recipeMapper.getRecommendRecipes(
                allergyMemberNo, kw, excludes, recipeType, cookingMethod, query.date());

        return new RecipeRecommendResponse(recipes);
    }

    // 내 레시피 조회 (GET /api/recipes/me) — 로그인 회원이 작성한 레시피 최신순 페이징. data = { recipes, pageInfo }
    //  Controller @RequestParam 은 형식(타입·기본값)만 보장하므로 값 범위는 여기서 본다
    //  (getCalorieRecommendRecipes·BookmarkService.getBookmarkList 와 동일 방식 — 어느 파라미터가 왜 틀렸는지 data 로).
    @Transactional(readOnly = true)
    public RecipeListResponse getMyRecipeList(Long memberNo, int page, int size) {
        if (page < 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, Map.of("page", "0 이상이어야 합니다."));
        }
        if (size < 1 || size > 50) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, Map.of("size", "1 이상 50 이하여야 합니다."));
        }

        int totalElements = recipeMapper.countMyRecipeList(memberNo);
        PageInfo pageInfo = new PageInfo(page, size);
        pageInfo.calculateTotalPage(totalElements);

        // 마지막 페이지 초과 조회 = 잘못된 요청 → 400 (빈 리스트 아님). page 0 또는 결과 0건은 정상.
        if (totalElements > 0 && page >= pageInfo.getTotalPages()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    Map.of("page", "존재하지 않는 페이지입니다."));
        }

        List<RecipeListItem> recipes = recipeMapper.getMyRecipeList(pageInfo.getOffset(), size, memberNo);
        return new RecipeListResponse(recipes, pageInfo);
    }

    // 정렬 화이트리스트. "popular"(인기순=조회수 내림차순) 만 인정하고 나머지(null·오타·대문자)는 전부 "latest"(최신순).
    // 이 값이 ${} 없이 매퍼 <choose> 로 가므로, 여기서 좁혀두면 SQL 주입 여지가 없다.
    private String normalizeSort(String sort) {
        return "popular".equals(sort) ? "popular" : "latest";
    }

    // 검색어 정규화. 양쪽 끝 공백 제거 후 비었으면 null(= 전체조회).
    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : escapeLike(trimmed);
    }

    // LIKE 메타문자(\ % _)를 이스케이프해서 "문자 그대로" 검색되게 한다. 쿼리는 ESCAPE '\'. 특수문자 거부는 안 함.
    private String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")  // \ 를 먼저 (뒤 치환의 이스케이프 문자와 겹치지 않게)
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    // 칼로리 기반 추천 (GET /api/recipes/recommend/calorie) — FE 가 계산한 totalCalories 를 끼니 수로 나눈 값(perMeal)에
    // CALORIE 가 가장 가까운 레시피 상위 N개. 회원 알러지 재료가 든 레시피는 제외.
    // 후보가 없으면(칼로리 미기재/전부 알러지 제외) 빈 리스트 + 200. 날짜기반 "오늘의 추천"과 별개.
    @Transactional(readOnly = true)
    public RecipeCalorieRecommendResponse getCalorieRecommendRecipes(long memberNo, Double totalCalories) {
        if (totalCalories == null || totalCalories <= 0 || totalCalories > TOTAL_CALORIES_MAX) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    Map.of("totalCalories", "0 초과 " + (int) TOTAL_CALORIES_MAX + " 이하의 값이 필요합니다."));
        }
        double perMeal = totalCalories / MEALS_PER_DAY;
        List<RecipeListItem> recipes =
                recipeMapper.getCalorieRecommendRecipes(memberNo, perMeal, RECOMMEND_COUNT);
        return new RecipeCalorieRecommendResponse(recipes);
    }

    // 제외 재료 목록 정규화: null/blank 항목 제거 + 각 항목 trim + LIKE 이스케이프.
    // 전부 비면 null 반환 (매퍼 <if> 에서 조건 생략).
    private List<String> normalizeExcludeMaterials(List<String> excludeMaterials) {
        if (excludeMaterials == null) {
            return null;
        }
        List<String> cleaned = excludeMaterials.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::escapeLike)
                .toList();
        return cleaned.isEmpty() ? null : cleaned;
    }

    // 상세 조회 — 집계 조회이므로 다중 쿼리(recipe ⨝ member / 재료 / 조리 단계) 결과를 조립.
    // 인증 선택: memberNo != null 이면 매퍼가 그 회원의 즐겨찾기 여부(isBookmarked)를 판정, 비로그인(null)이면 false 고정.
    // 엔드포인트는 permitAll 유지 (토큰 없어도 200). data = { recipe, materials, steps }
    // 조회 1회당 VIEW_COUNT +1 (그래서 readOnly 아님). 404 확인 후 카운트, 카운트 실패는 삼킴(조회는 성공).
    @Transactional
    public RecipeDetailResponse getRecipe(Long recipeNo, Long memberNo) {
        RecipeDetailItem recipe = recipeMapper.getRecipeDetail(recipeNo, memberNo);
        if (recipe == null) {
            throw new CustomException(ErrorCode.RECIPE_NOT_FOUND);
        }

        // 조회수 +1. 실패해도(락 타임아웃·데드락 등) 조회는 진행 — MyBatis 라 catch 후 tx 정상 커밋.
        try {
            recipeMapper.increaseViewCount(recipeNo);
        } catch (RuntimeException e) {
            log.warn("viewCount 증가 실패 recipeNo={}", recipeNo, e);
        }

        return RecipeDetailResponse.of(
                recipe,
                recipeMapper.getMaterialsByRecipeNo(recipeNo),
                recipeMapper.getStepsByRecipeNo(recipeNo));
    }

    // 레시피 삭제 — 소프트 삭제 (RECIPES.DEL_YN='Y')만. 작성자 본인만.
    // MATERIAL·RECIPE_STEPS 행과 S3 객체는 그대로 둔다 (살아있는 레시피로만 접근되므로 캐스케이드 불필요).
    @Transactional
    public void deleteRecipe(Long recipeNo, Long memberNo) {
        Recipe existing = recipeMapper.getRecipeByNo(recipeNo);
        if (existing == null) {
            throw new CustomException(ErrorCode.RECIPE_NOT_FOUND);
        }
        if (!existing.getMemberNo().equals(memberNo)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        recipeMapper.updateRecipeDelYn(recipeNo, memberNo);
    }

    // ============================================================
    //  레시피 수정 — "최종 상태 기반" 갱신 (전부 삭제 후 재삽입 금지)
    //  요청의 materialList / stepList 가 그 레시피의 최종 상태다. 기존 DB 행과 대조해
    //  materialNo/stepNo 있으면 UPDATE, null 이면 INSERT, 요청에 없는 기존 행은 DELETE.
    // ============================================================
    @Transactional
    public void updateRecipe(Long recipeNo, RecipeUpdateRequest request, MultipartFile mainImg, Long memberNo) {
        validateRecipeUpdateRequest(request, mainImg);

        // 1. 대상 조회 — 없으면 404, 남의 레시피면 403. 기존 대표 이미지 값도 여기서 확보.
        Recipe existing = recipeMapper.getRecipeByNo(recipeNo);
        if (existing == null) {
            throw new CustomException(ErrorCode.RECIPE_NOT_FOUND);
        }
        if (!existing.getMemberNo().equals(memberNo)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        // S3 는 DB 트랜잭션 밖이라:
        //  - newUploadKeys  : 이번에 새로 올린 파일 → 흐름 중 예외 나면 catch 에서 삭제(등록과 동일)
        //  - oldKeysToDelete: 교체·삭제된 옛 파일 → 커밋이 성공한 뒤에만 삭제(롤백 시 유지)
        List<String> newUploadKeys = new ArrayList<>();
        List<String> oldKeysToDelete = new ArrayList<>();
        try {
            // 2. 대표 이미지 — 새 파일이 온 경우에만 교체, 아니면 기존 값을 그대로 다시 넣는다
            String mainImgName;
            String mainImgUrl;
            if (mainImg != null && !mainImg.isEmpty()) {
                mainImgUrl = s3Service.upload(mainImg, DIR_RECIPE_MAIN, memberNo);
                mainImgName = mainImg.getOriginalFilename();
                newUploadKeys.add(extractS3Key(mainImgUrl));
                addKeyIfPresent(oldKeysToDelete, existing.getRecipesImgPath()); // 옛 대표 이미지(S3 버킷 URL)
            } else {
                mainImgName = existing.getRecipeMainImg();   // 기존 원본 파일명
                mainImgUrl = existing.getRecipesImgPath();   // 기존 S3 버킷 URL
            }
            recipeMapper.updateRecipe(Recipe.builder()
                    .recipeNo(recipeNo)
                    .memberNo(memberNo)             // WHERE 소유자 이중 확인용
                    .recipeTitle(request.recipeTitle())
                    .recipeInfo(request.recipeInfo())
                    .recipeMainImg(mainImgName)      // RECIPE_MAIN_IMG  = 원본 파일명
                    .recipesImgPath(mainImgUrl)      // RECIPES_IMG_PATH = S3 버킷 URL
                    .cookingMethod(request.cookingMethod())
                    .recipeType(request.recipeType())
                    .calorie(request.calorie())              // PATCH = 전체 교체: 안 보낸 선택 필드는 null 로 덮음
                    .carbohydrate(request.carbohydrate())
                    .protein(request.protein())
                    .fat(request.fat())
                    .sodium(request.sodium())
                    .mainMaterial(blankToNull(request.mainMaterial()))
                    .build());

            // 3. 재료 — 최종 상태 기반 갱신 (이미지 없어 단순)
            updateMaterials(recipeNo, request.materialList());

            // 4. 조리 단계 — 최종 상태 기반 갱신 (+ 이미지 교체, STEP_ORDER UNIQUE 충돌 회피)
            updateSteps(recipeNo, request.stepList(), newUploadKeys, oldKeysToDelete);
        } catch (RuntimeException e) {
            deleteQuietly(newUploadKeys);
            throw e;
        }

        // 5. 커밋이 성공한 뒤에만 옛 S3 객체 정리 (롤백되면 옛 파일을 그대로 둬야 하므로)
        registerAfterCommitCleanup(oldKeysToDelete);
    }

    // 요청 materialList 가 최종 상태. 기존 행과 대조해 UPDATE / INSERT / DELETE.
    private void updateMaterials(Long recipeNo, List<MaterialUpdateRequest> requestList) {
        Map<Long, Material> existingByNo = recipeMapper.getMaterialsByRecipeNo(recipeNo).stream()
                .collect(Collectors.toMap(Material::getMaterialNo, Function.identity()));

        // materialNo 를 보냈는데 이 레시피 소속이 아니면 잘못된 요청
        for (MaterialUpdateRequest m : requestList) {
            if (m.materialNo() != null && !existingByNo.containsKey(m.materialNo())) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }
        Set<Long> keepNos = collectIds(requestList.stream().map(MaterialUpdateRequest::materialNo));

        // (a) 기존 행 중 요청에 없는 것 → 삭제
        for (Long existingNo : existingByNo.keySet()) {
            if (!keepNos.contains(existingNo)) {
                recipeMapper.deleteMaterial(existingNo);
            }
        }
        // (b) 요청 항목 → materialNo 있으면 UPDATE, 없으면 INSERT
        for (MaterialUpdateRequest m : requestList) {
            if (m.materialNo() == null) {
                recipeMapper.insertMaterial(Material.builder()
                        .recipeNo(recipeNo)
                        .materialName(m.materialName())
                        .amount(m.amount())
                        .build());
            } else {
                recipeMapper.updateMaterial(Material.builder()
                        .materialNo(m.materialNo())
                        .materialName(m.materialName())
                        .amount(m.amount())
                        .build());
            }
        }
    }

    // 요청 stepList 가 최종 상태. 재료와 같은 대조 + 단계 이미지 처리 + STEP_ORDER 충돌 회피.
    // STEP_ORDER 는 프론트가 보낸 값(1..N)을 그대로 저장한다 (서버가 재계산하지 않음).
    private void updateSteps(Long recipeNo, List<StepUpdateRequest> requestList,
                             List<String> newUploadKeys, List<String> oldKeysToDelete) {
        Map<Long, RecipeStep> existingByNo = recipeMapper.getStepsByRecipeNo(recipeNo).stream()
                .collect(Collectors.toMap(RecipeStep::getStepNo, Function.identity()));

        for (StepUpdateRequest s : requestList) {
            if (s.stepNo() != null && !existingByNo.containsKey(s.stepNo())) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }
        Set<Long> keepNos = collectIds(requestList.stream().map(StepUpdateRequest::stepNo));

        // (a) 요청에 없는 기존 단계 → 행 삭제 + 그 단계 이미지 삭제 예약
        for (RecipeStep existingStep : existingByNo.values()) {
            if (!keepNos.contains(existingStep.getStepNo())) {
                recipeMapper.deleteRecipeStep(existingStep.getStepNo());
                addKeyIfPresent(oldKeysToDelete, existingStep.getStepImgPath());
            }
        }

        // (b) 살아남는 기존 단계들의 STEP_ORDER 를 잠깐 +1000 (UNIQUE(RECIPE_NO, STEP_ORDER) 충돌 회피).
        //     삭제를 먼저 했으니 남은 행만 밀린다. 신규 단계만 있으면 밀 게 없다.
        boolean hasSurviving = existingByNo.keySet().stream().anyMatch(keepNos::contains);
        if (hasSurviving) {
            recipeMapper.bumpStepOrders(recipeNo);
        }

        // (c) 요청 항목 → UPDATE / INSERT (+ 이미지). STEP_IMG = 원본명 / STEP_IMG_PATH = S3 버킷 URL
        //     이미지 4갈래: 새 파일(교체) > removeStepImg=true(삭제) > 기존 단계(유지) > 신규 단계(없음)
        for (StepUpdateRequest s : requestList) {
            MultipartFile img = s.stepImg();
            boolean hasNewImg = img != null && !img.isEmpty();
            boolean removeImg = Boolean.TRUE.equals(s.removeStepImg());

            String stepImgName;
            String stepImgUrl;
            if (hasNewImg) {
                stepImgUrl = s3Service.upload(img, DIR_RECIPE_STEP, recipeNo);
                stepImgName = img.getOriginalFilename();
                newUploadKeys.add(extractS3Key(stepImgUrl));
            } else if (removeImg) {
                stepImgName = null;   // 이미지 삭제
                stepImgUrl = null;
            } else if (s.stepNo() != null) {
                RecipeStep existingStep = existingByNo.get(s.stepNo());  // 기존 단계, 이미지 미변경 → 값 유지
                stepImgName = existingStep.getStepImg();
                stepImgUrl = existingStep.getStepImgPath();
            } else {
                stepImgName = null;   // 신규 단계, 이미지 없음
                stepImgUrl = null;
            }

            if (s.stepNo() == null) {
                recipeMapper.insertRecipeStep(RecipeStep.builder()
                        .recipeNo(recipeNo)
                        .stepInfo(s.stepInfo())
                        .stepImg(stepImgName)      // STEP_IMG = 원본 파일명
                        .stepOrder(s.stepOrder())
                        .stepImgPath(stepImgUrl)   // STEP_IMG_PATH = S3 버킷 URL
                        .build());
            } else {
                // 교체(hasNewImg) 또는 삭제(removeImg) 시 옛 S3 객체 정리 예약
                if (hasNewImg || removeImg) {
                    addKeyIfPresent(oldKeysToDelete, existingByNo.get(s.stepNo()).getStepImgPath());
                }
                recipeMapper.updateRecipeStep(RecipeStep.builder()
                        .stepNo(s.stepNo())
                        .stepInfo(s.stepInfo())
                        .stepImg(stepImgName)
                        .stepOrder(s.stepOrder())
                        .stepImgPath(stepImgUrl)
                        .build());
            }
        }
    }

    // 형식 검증은 DTO @Valid, 여기선 교차 필드·업로드 정합성만
    private void validateRecipeUpdateRequest(RecipeUpdateRequest request, MultipartFile mainImg) {
        // 대표 이미지는 "미전송(null)" 은 정상(미변경), "전송했는데 빈 파일" 만 오류
        if (mainImg != null && mainImg.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        // STEP_ORDER 중복 금지 (UK_RECIPE_STEPS_ORDER 위반 사전 차단)
        List<Integer> orders = request.stepList().stream().map(StepUpdateRequest::stepOrder).toList();
        if (orders.stream().distinct().count() != orders.size()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // null 을 뺀 id 집합
    private Set<Long> collectIds(Stream<Long> ids) {
        return ids.filter(Objects::nonNull).collect(Collectors.toSet());
    }

    // 선택 문자열 필드(mainMaterial 등) 정규화: null / 공백뿐 → null, 그 외 trim
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    // S3 버킷 URL 이 있으면 버킷 키로 바꿔 목록에 추가
    private void addKeyIfPresent(List<String> keys, String url) {
        if (url != null && !url.isBlank()) {
            keys.add(extractS3Key(url));
        }
    }

    // 커밋이 성공하면 옛 S3 객체를 best-effort 삭제. 트랜잭션 동기화가 없으면(단위 테스트 등) 즉시 삭제.
    private void registerAfterCommitCleanup(List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(keys);
                }
            });
        } else {
            deleteQuietly(keys);
        }
    }

    // 형식 검증은 DTO @Valid, 여기선 교차 필드·업로드 정합성만
    private void validateRecipeCreateRequest (RecipeCreateRequest request, MultipartFile mainImg) {
        if (mainImg == null || mainImg.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        // STEP_ORDER 중복 금지 (UK_RECIPE_STEPS_ORDER 위반 사전 차단)
        List<Integer> orders = request.stepList().stream().map(StepCreateRequest::stepOrder).toList();
        if (orders.stream().distinct().count() != orders.size()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
    
    // bookmark 도메인에서 레시피 존재·활성(DEL_YN='N') 검증용. 기존 getRecipeByNo 재사용 — 기존 코드 미수정, 추가만.
    // 추후 recipe 담당이 이 부분 참고해 재정리 예정.
    // recipeNo == null 가드: getRecipeByNo(long) 언박싱 NPE(→500) 대신 404로 처리.
    @Transactional(readOnly = true)
    public void validateRecipeExists(Long recipeNo) {
        if (recipeNo == null || recipeMapper.getRecipeByNo(recipeNo) == null) {
            throw new CustomException(ErrorCode.RECIPE_NOT_FOUND);
        }
    }

    /** 올린 S3 파일을 best-effort로 삭제. 삭제 실패는 로그만 남기고 삼킨다(원래 예외를 덮지 않기 위해). */
    private void deleteQuietly(List<String> keys) {
        for (String key : keys) {
            try {
                s3Service.delete(key);
            } catch (RuntimeException ex) {
            	// 일부로 로그를 남겨서 S3파일이 삭제에 실패 했을경우 에러 로그를 띄워줌
                log.warn("S3 보상 삭제 실패 (수동 정리 필요) key={}", key, ex);
            }
        }
    }

    /** S3 접근 URL에서 버킷 키(경로)만 추출. https://bucket.s3.ap-northeast-2.amazonaws.com/recipes/1/x.jpg → recipes/1/x.jpg */
    private String extractS3Key(String url) {
        String path = URI.create(url).getPath();
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
