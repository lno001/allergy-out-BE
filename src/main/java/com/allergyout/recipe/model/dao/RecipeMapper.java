package com.allergyout.recipe.model.dao;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

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
}
