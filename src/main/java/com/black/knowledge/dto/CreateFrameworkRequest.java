package com.black.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

/**
 * 创建知识框架请求
 */
@Data
public class CreateFrameworkRequest {
    @NotBlank(message = "框架名称不能为空")
    private String name;

    private String subject;
    private String description;

    @Valid
    private List<FrameworkCategoryRequest> categories;
}
