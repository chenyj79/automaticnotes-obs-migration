package com.black.knowledge.dto;

import lombok.Data;

/**
 * 更新知识框架请求
 */
@Data
public class UpdateFrameworkRequest {
    private String name;
    private String subject;
    private String description;
}
