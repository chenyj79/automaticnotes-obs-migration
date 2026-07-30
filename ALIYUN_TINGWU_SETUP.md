# 阿里云通义听悟ASR集成说明

## 概述

本项目已集成阿里云通义听悟（Tingwu）离线转写服务，支持音视频文件的语音识别。

参考文档：[阿里云通义听悟离线转写文档](https://help.aliyun.com/zh/tingwu/offline-transcribe-of-audio-and-video-files)

## 功能特点

- ✅ 支持音频文件转写（mp3、wav、m4a等格式）
- ✅ 支持视频文件转写（mp4、avi、mov等格式）
- ✅ 自动语种识别（auto模式）
- ✅ 自动上传音频到OSS获取可访问URL
- ✅ 异步任务处理，支持轮询查询结果
- ✅ 与现有ASR服务接口兼容

## 前置条件

### 1. 创建阿里云账号并获取凭证

1. 注册并实名认证阿里云账号
2. 在[访问控制RAM](https://ram.console.aliyun.com/)中创建AccessKey
   - AccessKey ID
   - AccessKey Secret

### 2. 创建通义听悟项目

1. 登录[通义听悟控制台](https://tingwu.console.aliyun.com/)
2. 创建新项目
3. 获取项目的 **AppKey**

### 3. 配置OSS（用于存储音频文件）

通义听悟需要HTTP/HTTPS可访问的文件URL，因此需要先将音频文件上传到OSS。

确保已配置OSS：
- OSS Bucket
- OSS Endpoint
- OSS AccessKey（可以与通义听悟使用相同的AccessKey）

## 配置说明

在 `application.yml` 中配置：

```yaml
# 阿里云通义听悟配置
asr:
  aliyun:
    accessKeyId: ${ALIYUN_ACCESS_KEY_ID:}  # 阿里云AccessKey ID
    accessKeySecret: ${ALIYUN_ACCESS_KEY_SECRET:}  # 阿里云AccessKey Secret
    appKey: ${ALIYUN_APP_KEY:}  # 通义听悟AppKey
    endpoint: ${ALIYUN_TINGWU_ENDPOINT:tingwu.cn-shanghai.aliyuncs.com}  # 服务端点
    regionId: ${ALIYUN_REGION_ID:cn-shanghai}  # 区域ID
    pollInterval: 60  # 轮询间隔（秒），建议60秒
    maxWaitTime: 10800  # 最大等待时间（秒），默认3小时

# 华为云OBS配置（必须配置，用于上传音频文件）
huaweicloud:
  obs:
    endpoint: ${HUAWEICLOUD_OBS_ENDPOINT:obs.cn-north-4.myhuaweicloud.com}
    access-key-id: ${HUAWEICLOUD_OBS_ACCESS_KEY_ID:}
    access-key-secret: ${HUAWEICLOUD_OBS_ACCESS_KEY_SECRET:}
    bucketName: ${HUAWEICLOUD_OBS_BUCKET_NAME:}
```

## 工作流程

1. **上传视频到OBS**
   - 获取可公网访问的URL

2. **创建转写任务**
   - 调用通义听悟API创建转写任务
   - 返回TaskId

3. **轮询查询结果**
   - 按配置的间隔（默认60秒）轮询任务状态
   - 任务完成后提取转写文本

4. **返回结果**
   - 返回转写片段列表

## API限制

根据[官方文档](https://help.aliyun.com/zh/tingwu/offline-transcribe-of-audio-and-video-files)：

- **CreateTask QPS限制**: 20次/秒（用户级别）
- **GetTaskInfo QPS限制**: 100次/秒（用户级别）
- **文件大小**: 不超过6GB
- **音频时长**: 不超过6小时
- **处理时间**: 3小时内完成（大规模数据除外）

## 注意事项

1. **OSS文件访问权限**
   - 如果OSS文件为私有，需要生成预签名URL（有效期至少3小时）
   - 如果OSS文件为公开，可以直接使用OSS URL

2. **URL有效期**
   - 提交的音频文件URL有效期应不低于3小时
   - 系统会自动生成预签名URL

3. **轮询频率**
   - 建议轮询间隔为60秒，避免触发限流
   - 不要设置过短的轮询间隔

4. **语种设置**
   - 当前实现使用 `auto` 自动识别语种
   - 支持：中文、英文、粤语、日语、韩语
   - 如需指定语种，可修改 `SourceLanguage` 参数

## 错误处理

常见错误及解决方案：

1. **配置不完整**
   - 检查 `accessKeyId`、`accessKeySecret`、`appKey` 是否配置

2. **OSS上传失败**
   - 检查OSS配置是否正确
   - 检查OSS Bucket是否存在且有写入权限

3. **任务创建失败**
   - 检查音频URL是否可公网访问
   - 检查URL是否包含中文或特殊字符
   - 检查文件格式是否支持

4. **轮询超时**
   - 检查 `maxWaitTime` 配置是否足够
   - 对于大文件，可能需要更长的等待时间

## 后续优化建议

1. **回调方式支持**: 实现回调通知，避免轮询
2. **结果缓存**: 缓存转写结果，避免重复处理
3. **批量处理**: 支持批量提交转写任务
4. **进度查询**: 提供更详细的处理进度信息

