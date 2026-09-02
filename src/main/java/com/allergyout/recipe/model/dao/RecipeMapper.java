package com.allergyout.recipe.model.dao;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.allergyout.recipe.model.dto.RecipeListItem;
import com.allergyout.recipe.model.vo.Material;
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
}
