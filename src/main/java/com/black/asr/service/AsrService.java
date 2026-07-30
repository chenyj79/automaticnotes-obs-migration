package com.black.asr.service;

import com.black.asr.po.Video;
import com.black.asr.vo.VideoSegmentVo;
import com.black.asr.vo.VideoVo;

import java.util.List;

/**
 * ASR服务接口
 * 使用策略模式，方便扩展不同的ASR服务提供商
 */
public interface AsrService {
    /**
     * 将音视频文件转换为文字
     * @param video 视频信息
     * @return 分段内容
     * @throws com.black.exception.BusinessException 转写失败时抛出异常
     */
    List<VideoSegmentVo> transcribe(VideoVo video);

    /**
     * 检查服务是否可用
     * @return 是否可用
     */
    boolean isAvailable();
}

