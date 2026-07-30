package com.black.constant;

/**
 * 应用常量类
 * 统一管理项目中的常量字符串
 */
public final class AppConstants {

    private AppConstants() {
    }

    /**
     * OBS相关常量（兼容旧字段名 oss_*，实际存储为华为云OBS）
     */
    public static final class Oss {
        private Oss() {
        }

        public static final String VIDEO_FOLDER = "video";
        public static final String AUDIO_FOLDER = "audio";
        public static final String AVATAR_FOLDER = "avatar";
        public static final String FOLDER_SEPARATOR = "/";
    }

    /**
     * 文件格式常量
     */
    public static final class FileFormat {
        private FileFormat() {
        }

        // 音频格式
        public static final String MP3 = "mp3";
        public static final String WAV = "wav";
        public static final String M4A = "m4a";
        public static final String WMA = "wma";
        public static final String AAC = "aac";
        public static final String OGG = "ogg";
        public static final String AMR = "amr";
        public static final String FLAC = "flac";
        public static final String AIFF = "aiff";

        // 视频格式
        public static final String MP4 = "mp4";
        public static final String WMV = "wmv";
        public static final String M4V = "m4v";
        public static final String FLV = "flv";
        public static final String RMVB = "rmvb";
        public static final String DAT = "dat";
        public static final String MOV = "mov";
        public static final String MKV = "mkv";
        public static final String WEBM = "webm";
        public static final String AVI = "avi";
        public static final String MPEG = "mpeg";
        public static final String THREE_GP = "3gp";

        public static final String[] ALLOWED_VIDEO_FORMATS = {
                MP3, WAV, M4A, WMA, AAC, OGG, AMR, FLAC, AIFF, // 音频格式
                MP4, WMV, M4V, FLV, RMVB, DAT, MOV, MKV, WEBM, AVI, MPEG, THREE_GP // 视频格式
        };
    }

    /**
     * 阿里云通义听悟API相关常量
     */
    public static final class AliyunTingwu {
        private AliyunTingwu() {
        }

        // API版本
        public static final String API_VERSION = "2023-09-30";

        // API路径
        public static final String TASKS_URI = "/openapi/tingwu/v2/tasks";
        public static final String TASK_INFO_URI_FORMAT = "/openapi/tingwu/v2/tasks/%s";

        // 请求参数
        public static final String QUERY_TYPE = "type";
        public static final String QUERY_TYPE_OFFLINE = "offline";
        public static final String MODEL_DOMAIN_EDUCATION = "domain-education";

        // JSON字段名
        public static final String FIELD_APP_KEY = "AppKey";
        public static final String FIELD_INPUT = "Input";
        public static final String FIELD_FILE_URL = "FileUrl";
        public static final String FIELD_SOURCE_LANGUAGE = "SourceLanguage";
        public static final String FIELD_TASK_KEY = "TaskKey";
        public static final String FIELD_CODE = "Code";
        public static final String FIELD_DATA = "Data";
        public static final String FIELD_TASK_ID = "TaskId";
        public static final String FIELD_MESSAGE = "Message";
        public static final String FIELD_TASK_STATUS = "TaskStatus";
        public static final String FIELD_ERROR_CODE = "ErrorCode";
        public static final String FIELD_ERROR_MESSAGE = "ErrorMessage";
        public static final String FIELD_RESULT = "Result";
        public static final String FIELD_TRANSCRIPTION = "Transcription";
        public static final String FIELD_PARAGRAPHS = "Paragraphs";
        public static final String FIELD_PARAGRAPH_ID = "ParagraphId";
        public static final String FIELD_WORDS = "Words";
        public static final String FIELD_TEXT = "Text";
        public static final String FIELD_TEXT_POLISH_ENABLED = "TextPolishEnabled";
        public static final String FIELD_PARAMETERS = "Parameters";
        public static final String FIELD_TEXT_POLISH = "TextPolish";
        public static final String FIELD_FORMAL_PARAGRAPH_TEXT = "FormalParagraphText";
        public static final String FIELD_START = "Start";
        public static final String FIELD_END = "End";
        public static final String FIELD_MODEL = "Model";

        // 任务状态
        public static final String STATUS_ONGOING = "ONGOING";
        public static final String STATUS_COMPLETED = "COMPLETED";
        public static final String STATUS_FAILED = "FAILED";
        public static final String STATUS_INVALID = "INVALID";

        // 语言自动识别
        public static final String LANGUAGE_AUTO = "auto";

        // 任务Key前缀
        public static final String TASK_KEY_PREFIX = "task_";

        // HTTP相关
        public static final String HTTP_METHOD_GET = "GET";
        public static final String ENCODING_UTF8 = "utf-8";
        public static final int HTTP_SUCCESS_CODE = 200;
        public static final int HTTP_CONNECT_TIMEOUT = 10000;
        public static final int HTTP_READ_TIMEOUT = 10000;
        public static final int API_SUCCESS_CODE = 0;
    }

    /**
     * 业务消息常量
     */
    public static final class Message {
        private Message() {
        }

        // ==================== 通用消息 ====================
        // 成功消息
        public static final String QUERY_SUCCESS = "查询成功";
        public static final String UPDATE_SUCCESS = "修改成功";

        // ==================== 转写模块消息 ====================
        // 成功消息
        public static final String UPLOAD_SUCCESS = "文件上传成功";
        public static final String TRANSCRIBE_SUCCESS = "转写成功";
        public static final String VALIDATION_SUCCESS = "验证通过";
        public static final String PROCESS_SUCCESS = "视频处理完成";
        public static final String GET_VIDEO_LIST_SUCCESS = "获取视频列表成功";

        // 失败消息
        public static final String FILE_EMPTY = "文件为空";
        public static final String FILE_SIZE_EXCEEDED = "文件大小超过限制: %dMB";
        public static final String FILE_NAME_EMPTY = "文件名不能为空";
        public static final String UNSUPPORTED_FORMAT = "不支持的视频格式: %s，支持的格式: %s";
        public static final String UPLOAD_FAILED = "文件上传失败: %s";
        public static final String OSS_UPLOAD_FAILED = "文件上传到OBS失败";
        public static final String CONFIG_INCOMPLETE = "ASR配置不完整，请检查配置文件";
        public static final String CREATE_TASK_FAILED = "创建转写任务失败";
        public static final String TRANSCRIBE_TIMEOUT = "获取转写结果失败或超时";
        public static final String TRANSCRIBE_FAILED = "ASR转写失败: %s";
        public static final String GET_TASK_INFO_FAILED = "获取任务信息失败: %s";
        public static final String EXTRACT_TEXT_FAILED = "无法从Transcription JSON中提取文本，JSON内容: %s";
        public static final String DOWNLOAD_JSON_FAILED = "下载JSON失败，HTTP状态码: %d, 错误信息: %s";

        // ==================== 任务模块消息 ====================
        public static final String TASK_ID_NOT_FOUND = "任务ID不存在";
        public static final String TASK_NOT_FOUND = "任务不存在，ID: %d";
        public static final String TASK_ACCESS_DENIED = "无权访问此任务";

        // ==================== 用户模块消息 ====================
        // 成功消息
        public static final String REGISTER_SUCCESS = "注册成功";
        public static final String LOGIN_SUCCESS = "登录成功";

        // 失败消息
        public static final String USERNAME_EXISTS = "用户名已存在: %s";
        public static final String EMAIL_EXISTS = "邮箱已被注册: %s";
        public static final String PHONE_EXISTS = "手机号已被注册: %s";
        public static final String USER_NOT_FOUND = "用户不存在: %s";
        public static final String USER_NOT_FOUND_BY_ID = "用户不存在, ID: %d";
        public static final String BAD_CREDENTIALS = "用户名或密码错误";
        public static final String USER_DISABLED = "该用户已被禁用";
        public static final String OLD_PASSWORD_INCORRECT = "原密码错误";
        public static final String INVALID_USER_STATUS = "无效的用户状态: %s";
        public static final String INVALID_USER_ROLE = "无效的用户角色: %s";
        public static final String ACCESS_DENIED = "权限不足，无法访问该资源";
        public static final String UNAUTHORIZED = "未登录或认证已过期";
        public static final String VALIDATION_FAILED = "参数校验失败";

        // ==================== 知识模块消息 ====================
        // 成功消息
        public static final String KNOWLEDGE_CREATE_SUCCESS = "创建成功";
        public static final String KNOWLEDGE_DELETE_SUCCESS = "删除成功";
        public static final String KNOWLEDGE_EXTRACT_SUCCESS = "知识点提取成功";
        public static final String KNOWLEDGE_SCORE_SUCCESS = "视频评分计算成功";

        // 失败消息
        public static final String KNOWLEDGE_FRAMEWORK_NOT_FOUND = "知识框架不存在, ID: %d";
        public static final String KNOWLEDGE_POINT_NOT_FOUND = "知识点不存在, ID: %d";
        public static final String KNOWLEDGE_SCORE_NOT_FOUND = "视频评分不存在, 视频ID: %d";
        public static final String KNOWLEDGE_VIDEO_NOT_FOUND = "视频不存在, ID: %d";
        public static final String KNOWLEDGE_TRANSCRIPT_EMPTY = "视频转写文本为空，请先完成视频转写";
        public static final String KNOWLEDGE_ACCESS_DENIED = "无权操作此资源";
        public static final String KNOWLEDGE_FRAMEWORK_ID_NOT_FOUND = "知识框架ID不存在";
        public static final String KNOWLEDGE_POINT_ID_NOT_FOUND = "知识点ID不存在";
        public static final String KNOWLEDGE_RELATION_CONFLICT = "该关系已存在或与现有方向冲突（不允许循环依赖或重复）";
    }

    /**
     * 日期时间格式常量
     */
    public static final class DateTimeFormat {
        private DateTimeFormat() {
        }

        public static final String FILENAME_TIMESTAMP = "yyyyMMddHHmmss";
    }

    /**
     * 文件名相关常量
     */
    public static final class FileName {
        private FileName() {
        }

        public static final String DOT = ".";
        public static final String UNDERSCORE = "_";
        public static final int UUID_LENGTH = 8;
    }

    /**
     * HTTP协议相关常量
     */
    public static final class Http {
        private Http() {
        }

        public static final String PROTOCOL_HTTP = "http://";
        public static final String PROTOCOL_HTTPS = "https://";
    }
}
