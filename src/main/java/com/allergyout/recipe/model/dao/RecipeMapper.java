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

    // ---- 키워드 검색 (비회원) : 위 + 제목(RECIPE_TITLE) LIKE. keyword 는 Service 에서 이스케이프 완료(ESCAPE '\') ----
    List<RecipeListItem> getRecipeListByKeyword(@Param("offset") int offset,
                                                @Param("size") int size,
                                                @Param("keyword") String keyword);

    int countRecipeListByKeyword(@Param("keyword") String keyword);

    // ---- 키워드 검색 (회원) : 알러지 제외 + 제목(RECIPE_TITLE) LIKE ----
    List<RecipeListItem> getRecipeListForMemberByKeyword(@Param("offset") int offset,
                                                         @Param("size") int size,
                                                         @Param("memberNo") long memberNo,
                                                         @Param("keyword") String keyword);

    int countRecipeListForMemberByKeyword(@Param("memberNo") long memberNo,
                                          @Param("keyword") String keyword);

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

    // ---- 레시피 삭제 : 소프트 삭제 (RECIPES.DEL_YN='Y'). MATERIAL·RECIPE_STEPS·S3 는 그대로 둔다 ----
    // WHERE 에 memberNo 도 걸어 소유자 이중 확인 (Service 에서 이미 검사하지만 백스톱)
    void updateRecipeDelYn(@Param("recipeNo") long recipeNo, @Param("memberNo") long memberNo);
}
