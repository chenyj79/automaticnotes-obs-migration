package com.black.knowledge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 知识框架所有权校验注解
 * 标注在Controller方法上，AOP自动校验当前用户是否拥有指定框架
 * <p>
 * 用法：
 * {@code @CheckFrameworkOwner("#id")} — 从路径变量 id 获取 frameworkId
 * {@code @CheckFrameworkOwner("#request.frameworkId")} — 从请求体 DTO 获取
 * frameworkId
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckFrameworkOwner {
    /**
     * SpEL表达式，指定如何从方法参数中提取frameworkId
     * 例如：#id、#request.frameworkId
     */
    String value();
}
