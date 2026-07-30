package com.black.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 处理结果模型
 */
@Setter
@Getter
public class ProcessResult<T> {
    // Getters and Setters
    private boolean success;
    private String message;
    private T data;
    private Exception error;

    public ProcessResult() {
    }

    public ProcessResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public ProcessResult(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> ProcessResult<T> success(String message) {
        return new ProcessResult<>(true, message);
    }

    public static <T> ProcessResult<T> success(String message, T data) {
        return new ProcessResult<>(true, message, data);
    }

    public static <T> ProcessResult<T> failure(String message) {
        return new ProcessResult<>(false, message);
    }

    public static <T> ProcessResult<T> failure(String message, Exception error) {
        ProcessResult<T> result = new ProcessResult<>(false, message);
        result.setError(error);
        return result;
    }
}

