package com.allergyout.recipe.model.dto;

import java.util.List;

import org.springframework.web.bind.annotation.BindParam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

// multipart/form-data 폼 필드를 @ModelAttribute로 바인딩한다.
// 폼 key는 API 명세서([조리법] 등록 v1.4)가 대문자 스네이크(RECIPE_TITLE 등)라 @BindParam으로 매핑하고,
// 자바 필드는 팀 카멜케이스 규칙을 지킨다. 이미지 파일은 이 DTO에 두지 않고 Controller @RequestParam으로 분리해 받는다.
public record RecipeCreateRequest(

		@BindParam("RECIPE_TITLE") @NotBlank @Size(max = 50) // RECIPES.RECIPE_TITLE NVARCHAR2(50)
		String recipeTitle,

		@BindParam("RECIPE_INFO") @NotBlank @Size(max = 1000) // RECIPES.RECIPE_INFO NVARCHAR2(1000)
		String recipeInfo,

		@BindParam("MATERIAL_LIST") @NotEmpty @Valid List<MaterialCreateRequest> materialList,

		@BindParam("STEP_LIST") @NotEmpty @Valid List<StepCreateRequest> stepList) {
}
