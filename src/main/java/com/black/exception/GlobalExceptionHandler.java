package com.black.exception;

import com.black.model.ProcessResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 负责捕获Service层抛出的异常，并将其包装为ProcessResult返回给客户端
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProcessResult<Object>> handleBusinessException(BusinessException e) {
        ProcessResult<Object> result = ProcessResult.failure(e.getMessage());
        return ResponseEntity.badRequest().body(result);
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProcessResult<Object>> handleValidationException(MethodArgumentNotValidException e) {
        String errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProcessResult<Object> result = ProcessResult.failure("参数校验失败: " + errors);
        return ResponseEntity.badRequest().body(result);
    }

    /**
     * 处理权限不足异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProcessResult<Object>> handleAccessDeniedException(AccessDeniedException e) {
        ProcessResult<Object> result = ProcessResult.failure("权限不足，无法访问该资源");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
    }

    /**
     * 处理认证异常
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProcessResult<Object>> handleAuthenticationException(AuthenticationException e) {
        ProcessResult<Object> result = ProcessResult.failure("认证失败: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProcessResult<Object>> handleException(Exception e) {
        e.printStackTrace();
        ProcessResult<Object> result = ProcessResult.failure("系统错误: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
