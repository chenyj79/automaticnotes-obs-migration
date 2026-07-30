package com.black.exception;

import com.black.constant.AppConstants;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    // ==================== 文件相关异常工厂方法 ====================

    public static BusinessException fileEmpty() {
        return new BusinessException(AppConstants.Message.FILE_EMPTY);
    }

    public static BusinessException fileSizeExceeded(long maxSize) {
        return new BusinessException(String.format(AppConstants.Message.FILE_SIZE_EXCEEDED, maxSize / 1024 / 1024));
    }

    public static BusinessException fileNameEmpty() {
        return new BusinessException(AppConstants.Message.FILE_NAME_EMPTY);
    }

    public static BusinessException unsupportedFormat(String format) {
        String allowedFormats = String.join(", ", AppConstants.FileFormat.ALLOWED_VIDEO_FORMATS);
        return new BusinessException(String.format(AppConstants.Message.UNSUPPORTED_FORMAT, format, allowedFormats));
    }

    public static BusinessException ossUploadFailed() {
        return new BusinessException(AppConstants.Message.OSS_UPLOAD_FAILED);
    }

    public static BusinessException uploadFailed(String errorMessage) {
        return new BusinessException(String.format(AppConstants.Message.UPLOAD_FAILED, errorMessage));
    }

    // ==================== ASR相关异常工厂方法 ====================

    public static BusinessException configIncomplete() {
        return new BusinessException(AppConstants.Message.CONFIG_INCOMPLETE);
    }

    public static BusinessException createTaskFailed() {
        return new BusinessException(AppConstants.Message.CREATE_TASK_FAILED);
    }

    public static BusinessException createTaskFailedWithMessage(String message) {
        return new BusinessException(AppConstants.Message.CREATE_TASK_FAILED + ": " + message);
    }

    public static BusinessException transcribeTimeout() {
        return new BusinessException(AppConstants.Message.TRANSCRIBE_TIMEOUT);
    }

    public static BusinessException getTranscribeContentFailedWithMessage(String message) {
        return new BusinessException(String.format(AppConstants.Message.GET_TASK_INFO_FAILED, message));
    }

    public static BusinessException transcribeFailedWithMessage(String message) {
        return new BusinessException(String.format(AppConstants.Message.TRANSCRIBE_FAILED, message));
    }

    public static BusinessException segmentMissing() {
        throw new BusinessException("转写片段丢失，已回退到转写阶段，等待重新执行");
    }

    public static BusinessException extractedPointMissing() {
        throw new BusinessException("知识提取快照丢失，已回退到提取阶段，等待重新执行");
    }

    // ==================== 用户模块异常工厂方法 ====================

    public static BusinessException usernameExists(String username) {
        return new BusinessException(String.format(AppConstants.Message.USERNAME_EXISTS, username));
    }

    public static BusinessException emailExists(String email) {
        return new BusinessException(String.format(AppConstants.Message.EMAIL_EXISTS, email));
    }

    public static BusinessException phoneExists(String phone) {
        return new BusinessException(String.format(AppConstants.Message.PHONE_EXISTS, phone));
    }

    public static BusinessException userNotFound(String username) {
        return new BusinessException(String.format(AppConstants.Message.USER_NOT_FOUND, username));
    }

    public static BusinessException userNotFoundById(Long id) {
        return new BusinessException(String.format(AppConstants.Message.USER_NOT_FOUND_BY_ID, id));
    }

    public static BusinessException badCredentials() {
        return new BusinessException(AppConstants.Message.BAD_CREDENTIALS);
    }

    public static BusinessException userDisabled() {
        return new BusinessException(AppConstants.Message.USER_DISABLED);
    }

    public static BusinessException oldPasswordIncorrect() {
        return new BusinessException(AppConstants.Message.OLD_PASSWORD_INCORRECT);
    }

    public static BusinessException invalidUserStatus(String status) {
        return new BusinessException(String.format(AppConstants.Message.INVALID_USER_STATUS, status));
    }

    public static BusinessException invalidUserRole(String role) {
        return new BusinessException(String.format(AppConstants.Message.INVALID_USER_ROLE, role));
    }

    // ==================== 知识模块异常工厂方法 ====================

    public static BusinessException frameworkNotFound(Long id) {
        return new BusinessException(String.format(AppConstants.Message.KNOWLEDGE_FRAMEWORK_NOT_FOUND, id));
    }

    public static BusinessException knowledgePointNotFound(Long id) {
        return new BusinessException(String.format(AppConstants.Message.KNOWLEDGE_POINT_NOT_FOUND, id));
    }

    public static BusinessException videoNotFound(Long id) {
        return new BusinessException(String.format(AppConstants.Message.KNOWLEDGE_VIDEO_NOT_FOUND, id));
    }

    public static BusinessException scoreNotFound(Long videoId) {
        return new BusinessException(String.format(AppConstants.Message.KNOWLEDGE_SCORE_NOT_FOUND, videoId));
    }

    public static BusinessException transcriptEmpty() {
        return new BusinessException(AppConstants.Message.KNOWLEDGE_TRANSCRIPT_EMPTY);
    }

    public static BusinessException knowledgeAccessDenied() {
        return new BusinessException(AppConstants.Message.KNOWLEDGE_ACCESS_DENIED);
    }

    public static BusinessException aiExtractionFailed(String detail) {
        return new BusinessException("AI知识提取失败: " + detail);
    }

    public static BusinessException aiScoringFailed(String detail) {
        return new BusinessException("AI视频评分失败: " + detail);
    }

    public static BusinessException frameworkIdNotFound() {
        return new BusinessException(AppConstants.Message.KNOWLEDGE_FRAMEWORK_ID_NOT_FOUND);
    }

    public static BusinessException knowledgePointIdNotFound() {
        return new BusinessException(AppConstants.Message.KNOWLEDGE_POINT_ID_NOT_FOUND);
    }

    public static BusinessException knowledgeRelationConflict() {
        return new BusinessException(AppConstants.Message.KNOWLEDGE_RELATION_CONFLICT);
    }

    public static BusinessException draftNotFound(Long id) {
        return new BusinessException(String.format("草稿不存在: %d", id));
    }

    public static BusinessException draftAlreadyReviewed(Long id) {
        return new BusinessException(String.format("草稿已审核: %d", id));
    }

    public static BusinessException aiResponseIsEmpty() {
        return new BusinessException("大模型返回内容为空");
    }

    // ==================== 任务模块相关异常工厂方法 ====================
    public static BusinessException taskIdNotFound() {
        return new BusinessException(AppConstants.Message.TASK_ID_NOT_FOUND);
    }

    public static BusinessException taskNotFound(Long taskId) {
        return new BusinessException(String.format(AppConstants.Message.TASK_NOT_FOUND, taskId));
    }

    public static BusinessException taskAccessDenied() {
        return new BusinessException(AppConstants.Message.TASK_ACCESS_DENIED);
    }

    public static BusinessException unauthorized() {
        return new BusinessException(AppConstants.Message.UNAUTHORIZED);
    }

    public static BusinessException operationFailed(String message) {
        return new BusinessException(message);
    }

    // ==================== 重试与基准测试异常工厂方法 ====================

    public static BusinessException taskNotRetryable(Long taskId, String status) {
        return new BusinessException(String.format("任务 %d 当前状态为 %s，不支持重试", taskId, status));
    }

    public static BusinessException benchmarkFailed(String detail) {
        return new BusinessException("ASR 基准测试失败: " + detail);
    }

    public static BusinessException benchmarkNotFound(Long videoId) {
        return new BusinessException(String.format("视频 %d 的基准测试结果不存在", videoId));
    }

    public static BusinessException taskExecutionFailed(Long taskId, String message, Throwable cause) {
        return new BusinessException(String.format("任务 %d 执行失败，准备重试: %s", taskId, message), cause);
    }
}
