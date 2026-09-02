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
import com.allergyout.recipe.model.dto.RecipeListResponse;
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

    // 목록 조회 — 최신순, OFFSET 페이징. data = { recipes, pageInfo }
    // 비회원(memberNo == null) : 삭제만 제외 / 회원 : 그 회원 알러지 재료가 든 레시피도 제외
    @Transactional(readOnly = true)
    public RecipeListResponse getRecipeList(int page, int size, Long memberNo) {
        validatePageParams(page, size);
        
        PageInfo pageInfo = new PageInfo(page, size);
        int offset = pageInfo.getOffset();

        List<RecipeListItem> recipes;
        int totalElements;
        if (memberNo == null) {
            recipes = recipeMapper.getRecipeList(offset, size);
            totalElements = recipeMapper.countRecipeList();
        } else {
            recipes = recipeMapper.getRecipeListForMember(offset, size, memberNo);
            totalElements = recipeMapper.countRecipeListForMember(memberNo);
        }

        pageInfo.calculateTotalPage(totalElements);
        return new RecipeListResponse(recipes, pageInfo);
    }

    // 상세 조회 — 집계 조회이므로 다중 쿼리(recipe ⨝ member / 재료 / 조리 단계) 결과를 조립.
    // 인증 없음. data = { recipe, materials, steps }
    @Transactional(readOnly = true)
    public RecipeDetailResponse getRecipe(Long recipeNo) {
        RecipeDetailItem recipe = recipeMapper.getRecipeDetail(recipeNo);
        if (recipe == null) {
            throw new CustomException(ErrorCode.RECIPE_NOT_FOUND);
        }

        // isBookmarked 는 현재 매퍼가 false(0) 고정으로 내려준다.
        // TODO: 즐겨찾기 기능 구현 시 —
        //   ① RecipeService 에 BookmarkService 주입
        //   ② Controller getRecipe 에 @AuthenticationPrincipal CustomUserDetails 추가 → memberNo 를 이 메서드로 전달
        //   ③ 여기서 memberNo != null && bookmarkService.isBookmarked(memberNo, recipeNo) 로 recipe 를 재조립
        //   ④ recipe-mapper.xml getRecipeDetail 의 '0 AS IS_BOOKMARKED' 제거
        return RecipeDetailResponse.of(
                recipe,
                recipeMapper.getMaterialsByRecipeNo(recipeNo),
                recipeMapper.getStepsByRecipeNo(recipeNo));
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
                    .recipeTitle(request.recipeTitle())
                    .recipeInfo(request.recipeInfo())
                    .recipeMainImg(mainImgName)      // RECIPE_MAIN_IMG  = 원본 파일명
                    .recipesImgPath(mainImgUrl)      // RECIPES_IMG_PATH = S3 버킷 URL
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

    // 형식(기본값·타입)은 Controller @RequestParam, 여기선 값 범위만 (page ≥ 0, 1 ≤ size ≤ 50)
    private void validatePageParams(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
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
