package com.allergyout.recipe.model.service;

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
        validateCreateRequest(request, mainImg);

        // S3는 DB 트랜잭션 밖이라 실패 시 수동 보상이 필요. 흐름 중 예외 나면 catch에서 되돌린다.
        CompensatingUpload upload = new CompensatingUpload(s3Service);
        try {
            // 1. 대표 이미지 업로드 (recipeNo가 아직 없어 memberNo로 키 구성)
            String mainImgUrl = upload.upload(mainImg, DIR_RECIPE_MAIN, memberNo);

            // 2. RECIPES INSERT — 생성 PK를 되받아야 해서 Map 파라미터
            Map<String, Object> recipeParam = new HashMap<>();
            recipeParam.put("memberNo", memberNo);
            recipeParam.put("recipeTitle", request.recipeTitle());
            recipeParam.put("recipeInfo", request.recipeInfo());
            recipeParam.put("recipeMainImg", mainImgUrl);
            recipeParam.put("recipesImgPath", requireOriginalName(mainImg));
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
                    stepImgUrl = upload.upload(stepImg, DIR_RECIPE_STEP, recipeNo);
                    stepImgPath = requireOriginalName(stepImg);
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
            upload.rollbackQuietly();
            throw e;
        }
    }

    // 형식 검증은 DTO @Valid, 여기선 교차 필드·업로드 정합성만
    private void validateCreateRequest(RecipeCreateRequest request, MultipartFile mainImg) {
        if (mainImg == null || mainImg.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        // STEP_ORDER 중복 금지 (UK_RECIPE_STEPS_ORDER 위반 사전 차단)
        List<Integer> orders = request.stepList().stream().map(StepCreateRequest::stepOrder).toList();
        if (orders.stream().distinct().count() != orders.size()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String requireOriginalName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return name;
    }
}
