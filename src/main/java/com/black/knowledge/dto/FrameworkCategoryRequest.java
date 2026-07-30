package com.black.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FrameworkCategoryRequest {
    @NotBlank(message = "分类名称不能为空")
    private String name;

    @NotBlank(message = "分类定义不能为空")
    private String definition;
}

