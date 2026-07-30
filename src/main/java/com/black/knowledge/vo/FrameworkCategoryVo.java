package com.black.knowledge.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FrameworkCategoryVo {
	private Long id;
	private Long frameworkId;
	private String name;
	private String definition;
	private Integer sortOrder;
	private LocalDateTime createTime;
	private LocalDateTime updateTime;
}


