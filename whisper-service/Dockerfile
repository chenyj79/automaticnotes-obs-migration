FROM nvidia/cuda:12.1.1-runtime-ubuntu22.04

LABEL maintainer="AutoNotes Team"
LABEL description="Whisper ASR Service - Dual GPU accelerated speech-to-text with punctuation restoration"

# 避免 tzdata 等包的交互式提示
ENV DEBIAN_FRONTEND=noninteractive
ENV PYTHONUNBUFFERED=1

# 替换容器内 APT 源为阿里云镜像，加速构建（仅影响容器内）
RUN sed -i 's/archive.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list \
    && sed -i 's/security.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list

# 安装 Python 3.11 和 ffmpeg（用于视频音频处理）
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3.11 \
    python3.11-venv \
    python3-pip \
    ffmpeg \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 让 python3 指向 python3.11
RUN update-alternatives --install /usr/bin/python3 python3 /usr/bin/python3.11 1

WORKDIR /app

# ============ 安装 Python 依赖 ============

# 1. 先安装 PyTorch GPU 版本
#    RTX 3090 Ti (Ampere SM86) 需要 CUDA 11.1+，此处匹配基础镜像的 CUDA 12.1
RUN pip3 install --no-cache-dir \
    torch torchaudio \
    --index-url https://download.pytorch.org/whl/cu121

# 2. 安装其余依赖（使用阿里云 pip 镜像源加速）
COPY requirements.txt .
RUN pip3 install --no-cache-dir -r requirements.txt \
    -i https://mirrors.aliyun.com/pypi/simple/

# 复制应用代码
COPY main.py .

# ============ 模型存储策略 ============
# 不在镜像中预下载模型，改为通过 Volume 挂载到用户 home 目录。
# 首次启动时模型自动下载到挂载目录，后续重建容器无需重新下载。
#
# 启动命令示例：
#sudo docker run -d \
#  --name whisper-asr \
#  --gpus all \
#  --network host \
#  --restart unless-stopped \
#  -e HF_ENDPOINT=https://hf-mirror.com \
#  -v ~/whisper-models/whisper:/app/models \
#  -v ~/whisper-models/funasr:/app/funasr-cache \
#  whisper-asr:latest

# FunASR / ModelScope 的模型缓存目录（通过 volume 挂载到宿主机 home 目录）
ENV MODELSCOPE_CACHE=/app/funasr-cache

# ============ 运行时环境变量 ============
# 可在 docker run 时通过 -e 覆盖
ENV DEVICE=cuda
ENV COMPUTE_TYPE=float16
ENV WHISPER_MODEL=large-v3
ENV DOWNLOAD_TIMEOUT=600
ENV BATCH_SIZE=24
ENV GPU_DEVICES=0,1

EXPOSE 8000

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD python3 -c "import requests; r=requests.get('http://localhost:8000/health'); exit(0 if r.status_code==200 else 1)" || exit 1

CMD ["python3", "main.py"]
