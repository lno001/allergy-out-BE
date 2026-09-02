package com.allergyout.recipe.model.dao;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.allergyout.recipe.model.dto.RecipeDetailItem;
import com.allergyout.recipe.model.dto.RecipeListItem;
import com.allergyout.recipe.model.vo.Material;
import com.allergyout.recipe.model.vo.Recipe;
import com.allergyout.recipe.model.vo.RecipeStep;

@Mapper
public interface RecipeMapper {

    // 생성 PK(recipeNo)를 되받아야 해서 Map 파라미터
    // (불변 VO/record는 useGeneratedKeys가 키를 써넣지 못함). 채워진 recipeNo는 param.get("recipeNo")로 꺼낸다.
    void insertRecipe(Map<String, Object> param);

    // 키 안 되받는 INSERT는 VO 그대로
    void insertMaterial(Material material);

    void insertRecipeStep(RecipeStep step);

    // ---- 목록 조회 (비회원) : RECIPES ⨝ MEMBER, DEL_YN='N', 최신순, OFFSET 페이징 ----
    List<RecipeListItem> getRecipeList(@Param("offset") int offset, @Param("size") int size);

    int countRecipeList();

    // ---- 목록 조회 (회원) : 위 + 회원 알러지 재료가 들어간 레시피는 제외 ----
    List<RecipeListItem> getRecipeListForMember(@Param("offset") int offset,
                                                @Param("size") int size,
                                                @Param("memberNo") long memberNo);

    int countRecipeListForMember(long memberNo);

    // ---- 상세 조회 : 집계 조회이므로 다중 쿼리 + Service 조립 ----

    // RECIPES ⨝ MEMBER, DEL_YN='N'. 없으면 null.
    RecipeDetailItem getRecipeDetail(long recipeNo);

    List<Material> getMaterialsByRecipeNo(long recipeNo);

    List<RecipeStep> getStepsByRecipeNo(long recipeNo);

    // ---- 레시피 수정 : "최종 상태 기반" 갱신 (getMaterialsByRecipeNo / getStepsByRecipeNo 는 상세 조회와 공유) ----

    // 수정 대상 조회 (소유자 확인 + 기존 대표 이미지 값 확보). 없으면 null.
    Recipe getRecipeByNo(long recipeNo);

    // RECIPES UPDATE — 이미지 컬럼도 항상 채운다 (대표 이미지 미변경이면 Service 가 기존 값을 그대로 다시 넣음).
    void updateRecipe(Recipe recipe);

    void updateMaterial(Material material);

    void deleteMaterial(long materialNo);

    void updateRecipeStep(RecipeStep step);

    void deleteRecipeStep(long stepNo);

    // UNIQUE(RECIPE_NO, STEP_ORDER) 충돌 회피 : 남은 기존 단계들의 STEP_ORDER 를 잠깐 +1000 밀어둔다.
    void bumpStepOrders(long recipeNo);
}
