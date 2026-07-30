package com.black.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SuggestFrameworkCategoriesRequest {
    @NotBlank(message = "框架名称不能为空")
    private String name;

    private String subject;
    private String description;
}

