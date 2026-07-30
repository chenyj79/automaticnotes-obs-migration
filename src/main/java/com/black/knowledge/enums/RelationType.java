package com.black.knowledge.enums;

/**
 * 知识点关联关系类型枚举
 */
public enum RelationType {
    /** 前置知识：targetPoint 是 sourcePoint 的前置条件 */
    PREREQUISITE,
    /** 相关知识：两个知识点内容相关 */
    RELATED,
    /** 包含关系：sourcePoint 包含 targetPoint（父→子） */
    CONTAINS
}
