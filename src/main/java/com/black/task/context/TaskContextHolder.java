package com.black.task.context;

/**
 * 任务上下文持有者
 * 统一管理任务执行期间的 ThreadLocal 变量
 */
public class TaskContextHolder {
    private static final ThreadLocal<Long> TASK_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> ASR_ENGINE_HOLDER = new ThreadLocal<>();

    // ===== Task ID =====

    public static void setTaskId(Long taskId) {
        TASK_ID_HOLDER.set(taskId);
    }

    public static Long getTaskId() {
        return TASK_ID_HOLDER.get();
    }

    // ===== ASR Engine =====

    public static void setAsrEngine(String engine) {
        ASR_ENGINE_HOLDER.set(engine);
    }

    public static String getAsrEngine() {
        return ASR_ENGINE_HOLDER.get();
    }

    /**
     * 清理当前线程的所有上下文信息
     * 必须在任务执行结束后的 finally 块中调用，防止线程池复用导致的污染
     */
    public static void clear() {
        TASK_ID_HOLDER.remove();
        ASR_ENGINE_HOLDER.remove();
    }
}
