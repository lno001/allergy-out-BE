package com.allergyout.recipe.model.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.allergyout.global.exception.CustomException;
import com.allergyout.global.exception.ErrorCode;
import com.allergyout.recipe.model.dao.RecipeMapper;
import com.allergyout.recipe.model.dto.MaterialCreateRequest;
import com.allergyout.recipe.model.dto.RecipeCreateRequest;
import com.allergyout.recipe.model.dto.StepCreateRequest;
import com.allergyout.recipe.model.vo.Material;
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

        // S3는 DB 트랜잭션 밖이라, 흐름 중 예외가 나면 catch에서 올린 파일을 수동으로 지운다.
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
            recipeParam.put("recipeMainImg", mainImgUrl);
            recipeParam.put("recipesImgPath", mainImg.getOriginalFilename());
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
            for (StepCreateRequest s : request.stepList()) {
                String stepImgUrl = null;
                String stepImgPath = null;
                MultipartFile stepImg = s.stepImg();
                if (stepImg != null && !stepImg.isEmpty()) {
                    stepImgUrl = s3Service.upload(stepImg, DIR_RECIPE_STEP, recipeNo);
                    uploadedKeys.add(extractS3Key(stepImgUrl));
                    stepImgPath = stepImg.getOriginalFilename();
                }
                recipeMapper.insertRecipeStep(RecipeStep.builder()
                        .recipeNo(recipeNo)
                        .stepInfo(s.stepInfo())
                        .stepImg(stepImgUrl)
                        .stepOrder(s.stepOrder())
                        .stepImgPath(stepImgPath)
                        .build());
            }
        } catch (RuntimeException e) {
            deleteQuietly(uploadedKeys);
            throw e;
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
