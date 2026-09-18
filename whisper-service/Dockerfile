FROM ubuntu:22.04

LABEL maintainer="AutoNotes Team"
LABEL description="Whisper ASR Service - CPU speech-to-text with punctuation restoration"

# 避免 tzdata 等包的交互式提示
ENV DEBIAN_FRONTEND=noninteractive
ENV PYTHONUNBUFFERED=1

# 替换容器内 APT 源为阿里云镜像，加速构建（仅影响容器内）
RUN sed -i 's/archive.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list \
    && sed -i 's/security.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list

# 安装 Python 3.11 和 ffmpeg（用于视频音频处理）
# --mount=type=cache：apt 索引与包缓存到 BuildKit 缓存，失败重跑无需重下已获取的包
# 主源阿里云偶发对个别大包（libllvm15 ~30MB）稳定连接失败，故失败后自动切到中科大源重试
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt/lists,sharing=locked \
    apt-get update \
    && apt-get -o Acquire::Retries=5 -o Acquire::http::Timeout=30 install -y --no-install-recommends \
       python3.11 python3.11-venv python3-pip ffmpeg \
    || (echo "== 阿里云源失败，切换中科大源重试 ==" \
        && sed -i 's|mirrors.aliyun.com|mirrors.ustc.edu.cn|g' /etc/apt/sources.list \
        && apt-get update \
        && apt-get -o Acquire::Retries=5 -o Acquire::http::Timeout=30 install -y --no-install-recommends \
           python3.11 python3.11-venv python3-pip ffmpeg)

# 让 python3 指向 python3.11
RUN update-alternatives --install /usr/bin/python3 python3 /usr/bin/python3.11 1

WORKDIR /app

# ============ 安装 Python 依赖 ============

# 1. 安装纯 CPU 版 PyTorch（官方 CPU 索引，避免拉入 CUDA 相关依赖）
#    国内若 download.pytorch.org 缓慢，可改用 https://mirrors.aliyun.com/pytorch-wheels/cpu
RUN pip3 install --no-cache-dir \
    torch torchaudio \
    --index-url https://download.pytorch.org/whl/cpu

# 2. 安装其余依赖（使用阿里云 pip 镜像源加速）
COPY requirements.txt .
RUN pip3 install --no-cache-dir -r requirements.txt \
    -i https://mirrors.aliyun.com/pypi/simple/

# 复制应用代码
COPY main.py .

# ============ 模型存储策略 ============
# 不在镜像中预下载模型：docker-compose.yml 把仓库内 ./whisper-models 绑定挂载到 /app/models，
# 模型由仓库根目录的 download-model.ps1 预先下载（详见 whisper-MODEL-README.md）。
#
# WHISPER_MODEL 两种用法：
#   1) 模型名（如 large-v3-turbo）→ 走 HF_ENDPOINT 在线下载，落盘到 download_root=/app/models（持久化）；
#   2) 本地模型目录（本项目 compose 的用法）→ 直接加载，需要先跑 download-model.ps1。

# FunASR / ModelScope 的模型缓存目录（通过 volume 挂载到宿主机 home 目录）
ENV MODELSCOPE_CACHE=/app/funasr-cache

# ============ 运行时环境变量 ============
# 可在 docker run 时通过 -e 覆盖
ENV DEVICE=cpu
ENV COMPUTE_TYPE=int8
ENV WHISPER_MODEL=large-v3-turbo
ENV DOWNLOAD_TIMEOUT=600
ENV BATCH_SIZE=8

EXPOSE 8000

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD python3 -c "import requests; r=requests.get('http://localhost:8000/health'); exit(0 if r.status_code==200 else 1)" || exit 1

CMD ["python3", "main.py"]
