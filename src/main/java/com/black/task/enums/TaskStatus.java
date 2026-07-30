package com.black.task.enums;

import lombok.Getter;

@Getter
public enum TaskStatus {
    STATUS_PENDING("待处理"),
    STATUS_TRANSCRIPTING("文本转写中"),
    STATUS_EXTRACTING("知识提取中"),
    STATUS_SCORING("智能评分中"),
    STATUS_SUCCESS("成功"),
    STATUS_FAILED("失败"),
    STATUS_CANCELLED("已取消"),
    STATUS_RETRYING("重试中");

    private final String description;

    TaskStatus(String description){
        this.description = description;
    }

    public boolean isTerminal() {
        return this == STATUS_SUCCESS || this == STATUS_FAILED || this == STATUS_CANCELLED;
    }

    public boolean isRetryable() {
        return this == STATUS_FAILED;
    }

    public int stageOrder() {
        return switch (this) {
            case STATUS_PENDING -> 0;
            case STATUS_TRANSCRIPTING -> 1;
            case STATUS_EXTRACTING -> 2;
            case STATUS_SCORING -> 3;
            case STATUS_SUCCESS -> 99;
            default -> 0;
        };
    }
}
