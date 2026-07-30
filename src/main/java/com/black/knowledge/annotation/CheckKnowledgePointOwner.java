package com.black.knowledge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 知识点所有权校验注解
 * 标注在Controller方法上，AOP自动校验当前用户是否拥有指定知识点所属的框架
 * <p>
 * 用法：
 * {@code @CheckKnowledgePointOwner("#id")} — 从路径变量 id 获取 知识点ID
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckKnowledgePointOwner {
    /**
     * SpEL表达式，指定如何从方法参数中提取知识点ID
     */
    String value();
}
