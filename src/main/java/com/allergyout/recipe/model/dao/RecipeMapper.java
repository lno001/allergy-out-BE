package com.allergyout.recipe.model.dao;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.allergyout.recipe.model.dto.RecipeDetailItem;
import com.allergyout.recipe.model.dto.RecipeListItem;
import com.allergyout.recipe.model.dto.RecipeRecommendItem;
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

    // ---- 목록 조회 (GET /api/recipes) : 목록·검색·필터·정렬 통합 ----
    //  하나의 동적 쿼리로 조립한다. 조건은 전부 선택 — null/빈값이면 그 조건이 통째로 빠진다.
    //   offset/size      : 페이징 (Service 가 PageInfo 로 계산)
    //   memberNo         : null 이면 알러지 제외 안 함. 회원이라도 applyMyAllergy=false 면 Service 가 null 로 넘김
    //                      (Long 박싱 — 원시형이면 <if> null 체크 불가)
    //   keyword          : 제목(RECIPE_TITLE) 부분일치. Service 에서 LIKE 메타문자 이스케이프 완료 → 쿼리는 ESCAPE '\'
    //   excludeMaterials : 그 재료가 하나라도 든 레시피 제외. 각 항목 이스케이프 완료
    //   recipeType       : RECIPE_TYPE 완전일치
    //   cookingMethod    : COOKING_METHOD 완전일치
    //   sort             : "popular"(VIEW_COUNT DESC) | 그 외 최신순. Service 화이트리스트 통과값이라 ${} 없이 <choose> 로 분기
    //   bookmarkMemberNo : 즐겨찾기 표시용. 로그인 회원이면 그 회원 PK, 비로그인이면 null(→ IS_BOOKMARKED 0 고정).
    //                      알러지 제외용 memberNo 와 별개 — applyMyAllergy=false 여도 즐겨찾기 표시는 동작해야 하므로
    List<RecipeListItem> getRecipeList(@Param("offset") int offset,
                                       @Param("size") int size,
                                       @Param("memberNo") Long memberNo,
                                       @Param("bookmarkMemberNo") Long bookmarkMemberNo,
                                       @Param("keyword") String keyword,
                                       @Param("excludeMaterials") List<String> excludeMaterials,
                                       @Param("recipeType") String recipeType,
                                       @Param("cookingMethod") String cookingMethod,
                                       @Param("sort") String sort);

    // 위와 같은 조건(페이징·정렬 제외)으로 전체 건수. totalPages 계산용 — <where> 조각을 getRecipeList 와 공유한다.
    int countRecipeList(@Param("memberNo") Long memberNo,
                        @Param("keyword") String keyword,
                        @Param("excludeMaterials") List<String> excludeMaterials,
                        @Param("recipeType") String recipeType,
                        @Param("cookingMethod") String cookingMethod);

    // ---- 내 레시피 조회 (GET /api/recipes/me) : 로그인 회원이 작성한 레시피 최신순 페이징 ----
    //  응답 카드는 목록 조회와 동일한 RecipeListItem. RECIPES ⨝ MEMBER, 소유(MEMBER_NO) + DEL_YN='N',
    //  CREATE_DATE 최신순(tie-breaker RECIPE_NO DESC), OFFSET 페이징. 검색·필터·알러지 제외 없음(내 글이라 무의미).
    List<RecipeListItem> getMyRecipeList(@Param("offset") int offset,
                                        @Param("size") int size,
                                        @Param("memberNo") Long memberNo);

    // 위와 같은 소유 조건으로 전체 건수 (totalPages 계산용).
    int countMyRecipeList(@Param("memberNo") Long memberNo);

    // ---- 칼로리 기반 추천 (GET /api/recipes/recommend/calorie) : 끼니당 목표 칼로리에 가장 가까운 레시피 top N ----
    //  응답 카드는 목록 조회와 동일한 RecipeListItem (프론트가 같은 카드 컴포넌트 재사용).
    //  RECIPES ⨝ MEMBER, DEL_YN='N', CALORIE IS NOT NULL, 회원 알러지 재료 제외(getRecipeList 의 알러지 제외 서브쿼리와 동일).
    //  정렬 |CALORIE - perMeal| 오름차순 → VIEW_COUNT 내림 → CREATE_DATE 오름 → RECIPE_NO 오름 (완전 결정론).
    //  날짜기반 getRecommendRecipes 와 별개.
    List<RecipeListItem> getCalorieRecommendRecipes(@Param("memberNo") long memberNo,
                                                    @Param("perMeal") double perMeal,
                                                    @Param("count") int count);

    // ---- 상세 조회 : 집계 조회이므로 다중 쿼리 + Service 조립 ----

    // RECIPES ⨝ MEMBER, DEL_YN='N'. 없으면 null.
    //  memberNo : 로그인 회원이면 그 회원의 즐겨찾기 여부를 IS_BOOKMARKED(0/1)로 판정, 비로그인(null)이면 0 고정.
    RecipeDetailItem getRecipeDetail(@Param("recipeNo") long recipeNo, @Param("memberNo") Long memberNo);

    // 상세 조회 1회당 VIEW_COUNT + 1 (Service 가 404 확인 후 try/catch 로 호출)
    void increaseViewCount(long recipeNo);

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
    
    // 오늘의 추천 레시피 (날짜 시드). 카드는 RecipeRecommendItem — 즐겨찾기 여부(isBookmarked) 미포함.
    // memberNo = 알러지 제외용 (applyMyAllergy=false·비로그인이면 null).
    List<RecipeRecommendItem> getRecommendRecipes(@Param("memberNo") Long memberNo,
                                                  @Param("keyword") String keyword,
                                                  @Param("excludeMaterials") List<String> excludeMaterials,
                                                  @Param("recipeType") String recipeType,
                                                  @Param("cookingMethod") String cookingMethod,
                                                  @Param("date") String date);
}
