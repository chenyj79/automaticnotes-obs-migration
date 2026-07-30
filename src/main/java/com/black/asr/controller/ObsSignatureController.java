package com.black.asr.controller;

import com.black.model.ProcessResult;
import com.black.util.ObsUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static com.black.constant.AppConstants.FileName;
import static com.black.constant.AppConstants.DateTimeFormat;

/**
 * 华为云 OBS 直传签名控制器
 * 提供预签名 URL，供前端直接上传到华为云 OBS，无需经过后端
 */
@Slf4j
@RestController
@RequestMapping("/api/obs")
@RequiredArgsConstructor
public class ObsSignatureController {

    private final ObsUtil obsUtil;

    @Data
    public static class PostUploadResponse {
        private String postUrl;      // POST 目标地址
        private String policy;       // Base64 Policy
        private String signature;    // 签名
        private String accessKeyId;  // AK
        private String objectName;   // OBS 对象键
        private String obsUrl;       // 公开访问 URL
        private String bucket;
        private String endpoint;
    }

    /**
     * 获取 POST 表单直传签名（浏览器直传标准方式）
     */
    @GetMapping("/upload-signature")
    public ResponseEntity<ProcessResult<PostUploadResponse>> getUploadSignature(
            @RequestParam(defaultValue = "mp4") String fileExtension,
            @RequestParam(defaultValue = "video") String folder) {

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern(DateTimeFormat.FILENAME_TIMESTAMP));
        String uuid = UUID.randomUUID().toString().substring(0, FileName.UUID_LENGTH);
        String uniqueFileName = timestamp + FileName.UNDERSCORE + uuid + FileName.DOT + fileExtension;
        String objectName = folder + "/" + uniqueFileName;

        ObsUtil.PostSignatureResult sig = obsUtil.generatePostSignature(objectName, 7200);
        log.info("生成上传签名 - objectName: {}, postUrl: {}", objectName, sig.getPostUrl());

        // 生成预签名GET URL（7天有效），供ASR等服务下载视频
        String signedGetUrl = obsUtil.generatePresignedGetUrl(objectName, 604800);

        PostUploadResponse resp = new PostUploadResponse();
        resp.setPostUrl(sig.getPostUrl());
        resp.setPolicy(sig.getPolicy());
        resp.setSignature(sig.getSignature());
        resp.setAccessKeyId(sig.getAccessKeyId());
        resp.setObjectName(sig.getObjectKey());
        resp.setObsUrl(signedGetUrl);  // 用带签名的URL，ASR可直接读取
        resp.setBucket(sig.getBucket());
        resp.setEndpoint(sig.getEndpoint());

        log.info("返回上传签名成功 - objectName: {}", objectName);
        return ResponseEntity.ok(ProcessResult.success("获取上传签名成功", resp));
    }

    /**
     * 诊断接口：生成 POST 表单签名后从后端提交测试上传
     */
    @GetMapping("/self-test")
    public ResponseEntity<ProcessResult<String>> selfTest() {
        String objectName = "test/obs-self-test-" + System.currentTimeMillis() + ".txt";
        String content = "OBS POST form upload test at " + new java.util.Date();

        ObsUtil.PostSignatureResult sig = obsUtil.generatePostSignature(objectName, 300);
        StringBuilder result = new StringBuilder();
        result.append("PostUrl: ").append(sig.getPostUrl()).append("\n");
        result.append("ObjectKey: ").append(sig.getObjectKey()).append("\n");
        result.append("Bucket: ").append(obsUtil.getBucketName()).append("\n\n");

        try {
            // 用后端HTTP客户端模拟POST表单上传
            String boundary = "---OBSFormBoundary" + System.currentTimeMillis();
            URL url = new URL(sig.getPostUrl());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            java.io.OutputStream out = conn.getOutputStream();
            java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(out, "UTF-8"), true);

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"key\"\r\n\r\n");
            writer.append(sig.getObjectKey()).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"policy\"\r\n\r\n");
            writer.append(sig.getPolicy()).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"signature\"\r\n\r\n");
            writer.append(sig.getSignature()).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"AWSAccessKeyId\"\r\n\r\n");
            writer.append(sig.getAccessKeyId()).append("\r\n");

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"test.txt\"\r\n");
            writer.append("Content-Type: text/plain\r\n\r\n");
            writer.flush();
            out.write(content.getBytes());
            out.flush();
            writer.append("\r\n--").append(boundary).append("--\r\n");
            writer.close();

            int status = conn.getResponseCode();
            result.append("HTTP状态码: ").append(status).append("\n");
            if (status >= 200 && status < 300) {
                result.append("✅ POST表单上传成功\n");
                obsUtil.delete(objectName);
            } else {
                result.append("❌ 上传失败\n");
                try (java.io.InputStream err = conn.getErrorStream()) {
                    if (err != null) {
                        result.append("OBS错误响应:\n").append(new String(err.readAllBytes())).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            result.append("❌ 异常: ").append(e.toString());
            log.error("OBS自测异常", e);
        }

        return ResponseEntity.ok(ProcessResult.success("自测完成", result.toString()));
    }
}
