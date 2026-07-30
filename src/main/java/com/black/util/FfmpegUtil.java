package com.black.util;

import com.black.constant.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * FFmpeg工具类
 * 用于执行FFmpeg命令来处理音视频文件
 */
@Component
@Slf4j
public class FfmpegUtil {
    /**
     * 获取视频时长（秒）
     *
     * @param file 上传的视频文件
     * @return 视频时长（秒），如果获取失败则返回0
     */
    public int getVideoDuration(MultipartFile file) {
        Path tempFile = null;
        try {
            // 创建临时文件
            tempFile = Files.createTempFile("video_", "." + getFileExtension(file.getOriginalFilename()));
            Files.write(tempFile, file.getBytes());

            // 执行ffprobe命令获取视频时长
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "ffprobe", 
                    "-v", "quiet", 
                    "-show_entries", "format=duration", 
                    "-of", "csv=p=0", 
                    tempFile.toString()
            );
            
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String durationStr = reader.readLine();
            process.waitFor();

            if (durationStr != null && !durationStr.trim().isEmpty()) {
                double duration = Double.parseDouble(durationStr.trim());
                return (int) Math.round(duration);
            }

            log.warn("无法从视频文件获取时长: {}", file.getOriginalFilename());
            return 0;
        } catch (Exception e) {
            log.error("获取视频时长失败: {}", e.getMessage());
            return 0;
        } finally {
            // 清理临时文件
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("删除临时文件失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDot = filename.lastIndexOf(AppConstants.FileName.DOT);
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }
}