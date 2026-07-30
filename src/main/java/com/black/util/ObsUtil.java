package com.black.util;

import com.black.constant.AppConstants;
import com.black.exception.BusinessException;
import com.obs.services.ObsClient;
import com.obs.services.model.HttpMethodEnum;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.PutObjectRequest;
import com.obs.services.model.PutObjectResult;
import com.obs.services.model.TemporarySignatureRequest;
import com.obs.services.model.TemporarySignatureResponse;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 华为云 OBS 工具类
 * 用于上传文件到华为云 OBS
 *
 * @Author: mancanghai
 * @Create: 2025/12/24 - 23:30
 * @Version: v2.0 (migrated from Alibaba OSS to Huawei OBS)
 */
@Slf4j
@Component
@Data
@ConfigurationProperties("huaweicloud.obs")
public class ObsUtil {
    private String endpoint;        // OBS 终端节点，如 obs.cn-north-4.myhuaweicloud.com
    private String accessKeyId;     // 华为云 Access Key
    private String accessKeySecret; // 华为云 Secret Key
    private String bucketName;      // 桶名称

    private volatile ObsClient obsClient;

    private ObsClient getClient() {
        if (obsClient == null) {
            synchronized (this) {
                if (obsClient == null) {
                    obsClient = new ObsClient(accessKeyId, accessKeySecret, endpoint);
                }
            }
        }
        return obsClient;
    }

    @PreDestroy
    public void destroy() {
        if (obsClient != null) {
            try {
                obsClient.close();
            } catch (IOException e) {
                log.warn("关闭 OBS Client 异常", e);
            }
        }
    }

    /**
     * 封装通用的上传逻辑：生成唯一文件名、确定 ContentType、执行上传
     *
     * @param file       上传的文件
     * @param folderName OBS 目录名称
     * @return 包含 URL 和对象名称的结果对象
     */
    public UploadResult uploadFile(MultipartFile file, String folderName) {
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);

            // 生成唯一文件名
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern(AppConstants.DateTimeFormat.FILENAME_TIMESTAMP));
            String uuid = UUID.randomUUID().toString().substring(0, AppConstants.FileName.UUID_LENGTH);
            String uniqueFileName = timestamp + AppConstants.FileName.UNDERSCORE + uuid + AppConstants.FileName.DOT
                    + extension;

            // 构造对象名称
            String objectName = folderName + AppConstants.Oss.FOLDER_SEPARATOR + uniqueFileName;

            // 获取 ContentType
            String contentType = getContentType(extension);

            // 执行上传
            String url = upload(objectName, file.getInputStream(), contentType);
            return new UploadResult(url, objectName);
        } catch (IOException e) {
            throw BusinessException.uploadFailed(e.getMessage());
        }
    }

    /**
     * 获取文件扩展名
     */
    public String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDot = filename.lastIndexOf(AppConstants.FileName.DOT);
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }

    /**
     * 上传文件到 OBS
     *
     * @param objectName  OBS 中的对象名称（文件路径）
     * @param inputStream 文件输入流
     * @param contentType 文件MIME类型
     * @return 文件的访问URL
     */
    public String upload(String objectName, InputStream inputStream, String contentType) {
        ObsClient client = getClient();
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentDisposition("inline");
            metadata.setContentType(contentType);

            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, inputStream);
            putObjectRequest.setMetadata(metadata);

            PutObjectResult result = client.putObject(putObjectRequest);
            log.debug("OBS 上传成功 - objectName: {}, etag: {}", objectName, result.getEtag());
        } finally {
            // ObsClient 是线程安全的单例，不需要每次请求都关闭
        }

        // 返回公开访问 URL（虚拟主机风格）
        return buildPublicUrl(objectName);
    }

    /**
     * 生成预签名 GET URL（供 ASR 服务下载视频文件）
     */
    public String generatePresignedGetUrl(String objectName, long expireSeconds) {
        ObsClient client = getClient();
        TemporarySignatureRequest request = new TemporarySignatureRequest(HttpMethodEnum.GET, expireSeconds);
        request.setBucketName(bucketName);
        request.setObjectKey(objectName);
        TemporarySignatureResponse response = client.createTemporarySignature(request);
        return response.getSignedUrl();
    }

    /**
     * 生成 POST 表单直传签名（浏览器标准直传方式）
     */
    public PostSignatureResult generatePostSignature(String objectName, long expireSeconds) {
        long expires = System.currentTimeMillis() / 1000 + expireSeconds;
        String expiration = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .format(new java.util.Date(expires * 1000));

        // 构造 Policy JSON（S3 兼容格式）
        String policyJson = String.format(
                "{\"expiration\":\"%s\",\"conditions\":[" +
                "{\"bucket\":\"%s\"}," +
                "{\"key\":\"%s\"}," +
                "{\"x-obs-acl\":\"public-read\"}," +
                "[\"starts-with\",\"$Content-Type\",\"\"]," +
                "[\"content-length-range\",1,6442450944]]}",
                expiration, bucketName, objectName);
        log.debug("POST Policy JSON: {}", policyJson);
        String base64Policy = java.util.Base64.getEncoder().encodeToString(
                policyJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // HMAC-SHA1 签名
        String signature;
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            javax.crypto.spec.SecretKeySpec spec =
                    new javax.crypto.spec.SecretKeySpec(accessKeySecret.getBytes(
                            java.nio.charset.StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(spec);
            byte[] signed = mac.doFinal(base64Policy.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            signature = java.util.Base64.getEncoder().encodeToString(signed);
        } catch (Exception e) {
            log.error("POST签名生成失败", e);
            throw new RuntimeException("POST签名生成失败", e);
        }

        PostSignatureResult result = new PostSignatureResult();
        result.setPostUrl(String.format("https://%s.%s/", bucketName, endpoint));
        result.setObjectKey(objectName);
        result.setPolicy(base64Policy);
        result.setSignature(signature);
        result.setAccessKeyId(accessKeyId);
        result.setObsUrl(buildPublicUrl(objectName));
        result.setBucket(bucketName);
        result.setEndpoint(endpoint);
        log.info("POST签名生成成功 - objectName: {}, postUrl: {}, bucket: {}", objectName, result.getPostUrl(), bucketName);
        return result;
    }

    @Data
    public static class PostSignatureResult {
        private String postUrl;      // POST 目标地址
        private String objectKey;    // 对象键
        private String policy;       // Base64 编码的 Policy
        private String signature;    // 签名
        private String accessKeyId;  // AK
        private String obsUrl;       // 上传后的公开访问 URL
        private String bucket;
        private String endpoint;
    }

    /**
     * 删除 OBS 中的文件
     *
     * @param objectName OBS 对象名称
     * @return 是否删除成功
     */
    public boolean delete(String objectName) {
        ObsClient client = getClient();
        try {
            client.deleteObject(bucketName, objectName);
            return true;
        } catch (Exception e) {
            log.warn("删除 OBS 文件失败 - objectName: {}, error: {}", objectName, e.getMessage());
            return false;
        }
    }

    /**
     * 构造 OBS 公开访问 URL
     * 虚拟主机风格：https://{bucketName}.{endpoint}/{objectName}
     */
    public String buildPublicUrl(String objectName) {
        return String.format("https://%s.%s/%s", bucketName, endpoint, objectName);
    }

    /**
     * 根据文件扩展名获取 ContentType
     *
     * @param extension 文件扩展名（不含点）
     * @return MIME类型
     */
    public String getContentType(String extension) {
        if (extension == null || extension.isEmpty()) {
            return "application/octet-stream";
        }

        String ext = extension.toLowerCase();
        return switch (ext) {
            // 视频格式
            case "mp4" -> "video/mp4";
            case "avi" -> "video/x-msvideo";
            case "mov" -> "video/quicktime";
            case "mkv" -> "video/x-matroska";
            case "flv" -> "video/x-flv";
            case "wmv" -> "video/x-ms-wmv";
            case "m4v" -> "video/x-m4v";
            case "3gp" -> "video/3gpp";
            case "webm" -> "video/webm";
            case "rmvb" -> "video/vnd.rn-realvideo";
            case "dat", "mpeg" -> "video/mpeg";
            // 音频格式
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "m4a" -> "audio/mp4";
            case "wma" -> "audio/x-ms-wma";
            case "aac" -> "audio/aac";
            case "ogg" -> "audio/ogg";
            case "amr" -> "audio/amr";
            case "flac" -> "audio/flac";
            case "aiff" -> "audio/aiff";
            // 图片格式（头像用）
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "application/octet-stream";
        };
    }

    @Data
    public static class UploadResult {
        private String url;
        private String objectName;

        public UploadResult() {}
        public UploadResult(String url, String objectName) {
            this.url = url;
            this.objectName = objectName;
        }
    }
}
