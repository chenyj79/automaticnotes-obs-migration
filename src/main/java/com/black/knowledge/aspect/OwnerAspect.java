package com.black.knowledge.aspect;

import com.black.exception.BusinessException;
import com.black.knowledge.annotation.CheckFrameworkOwner;
import com.black.knowledge.annotation.CheckKnowledgePointOwner;
import com.black.knowledge.service.KnowledgeFrameworkService;
import com.black.knowledge.service.KnowledgePointService;
import com.black.task.service.VideoTaskService;
import com.black.user.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 所有权校验切面
 * 拦截标注了 @CheckFrameworkOwner 和 @CheckKnowledgePointOwner 的方法
 * 自动校验当前用户是否拥有目标资源
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OwnerAspect {

    private final KnowledgeFrameworkService frameworkService;
    private final KnowledgePointService knowledgePointService;
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 校验框架所有权
     */
    @Before("@annotation(checkFrameworkOwner)")
    public void checkFrameworkOwnership(JoinPoint joinPoint, CheckFrameworkOwner checkFrameworkOwner) {
        Long userId = getCurrentUserId();
        Long frameworkId = extractValue(joinPoint, checkFrameworkOwner.value(), Long.class);
        if (frameworkId == null) {
            throw BusinessException.frameworkIdNotFound();
        }
        frameworkService.getFrameworkAndCheckOwner(frameworkId, userId);
    }

    /**
     * 校验知识点所有权（通过知识点 → 框架 → 用户的关系链校验）
     */
    @Before("@annotation(checkPointOwner)")
    public void checkKnowledgePointOwnership(JoinPoint joinPoint, CheckKnowledgePointOwner checkPointOwner) {
        Long userId = getCurrentUserId();
        Long pointId = extractValue(joinPoint, checkPointOwner.value(), Long.class);
        if (pointId == null) {
            throw BusinessException.knowledgePointIdNotFound();
        }
        // 通过知识点ID查找其所属的frameworkId，再校验框架所有权
        Long frameworkId = knowledgePointService.getFrameworkIdByPointId(pointId);
        frameworkService.getFrameworkAndCheckOwner(frameworkId, userId);
    }

    /**
     * 获取当前登录用户ID
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw BusinessException.unauthorized();
        }
        return loginUser.getId();
    }

    /**
     * 通过SpEL表达式从方法参数中提取值
     */
    private <T> T extractValue(JoinPoint joinPoint, String spel, Class<T> type) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        return parser.parseExpression(spel).getValue(context, type);
    }
}
