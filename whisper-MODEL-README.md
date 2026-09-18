# Whisper 模型预下载说明（部署 Whisper 前必做）

本项目本地 Whisper ASR 服务需要预下载 `faster-whisper large-v3-turbo` 模型（约 1.6GB）。
**模型文件体积较大，已被 `.gitignore` 排除，不会随代码仓库分发。** 因此拉取代码后、
`docker compose up` 之前，必须先在本机运行一次模型下载脚本。

## 为什么需要手动下载

- `whisper-models/` 目录在 `.gitignore` 中，克隆仓库后默认不存在。
- `docker-compose.yml` 中 `whisper-svc` 通过 `./whisper-models:/app/models` 绑定挂载模型目录，
  并把 `WHISPER_MODEL` 指向该目录下的 `mobiuslabsgmbh--faster-whisper-large-v3-turbo`。
- 若未提前下载，Whisper 容器启动时会尝试联网拉取 1.6GB 模型，极易因网络超时导致容器反复重启，
  健康检查一直停留在 `starting`。

## 操作步骤（Windows）

在仓库根目录执行：

```powershell
.\download-model.ps1
```

脚本会：

1. 在仓库内创建 `whisper-models/models/mobiuslabsgmbh--faster-whisper-large-v3-turbo/` 目录；
2. 从 **hf-mirror 镜像**（默认 `https://hf-mirror.com`，可用环境变量 `HF_ENDPOINT` 覆盖）断点续传下载以下 5 个文件：

| 文件                     | 说明           |
|--------------------------|----------------|
| `model.bin`              | 主模型权重(~1.6GB) |
| `config.json`            | 模型配置       |
| `tokenizer.json`         | 分词器         |
| `preprocessor_config.json` | 预处理器配置 |
| `vocabulary.json`        | 词表           |

每文件最多重试 50 轮，支持断点续传，网络中断后可重复执行继续下载，无需重新开始。

### 完成后校验

下载完成后确认以下目录结构存在（与 docker-compose.yml 挂载路径一致）：

```
whisper-models/
└── models/
    └── mobiuslabsgmbh--faster-whisper-large-v3-turbo/
        ├── model.bin
        ├── config.json
        ├── tokenizer.json
        ├── preprocessor_config.json
        └── vocabulary.json
```

## 针对不同系统的提示

- **Windows PowerShell**：直接执行 `.\download-model.ps1`。
- **Linux / macOS**：本项目使用 `curl.exe`，请在安装 curl 后执行或用等价命令手动下载上述 5 个文件到对应目录。
- **网络受限环境**：`download-model.ps1` 默认就走 `https://hf-mirror.com`（国内直连 huggingface.co 会超时）。
  如需换源，设置环境变量后执行，例如：`$env:HF_ENDPOINT="https://hf-mirror.com"; .\download-model.ps1`。
  容器侧的在线下载由 `docker-compose.yml` 里的 `HF_ENDPOINT` + `HF_HUB_DISABLE_XET=1` 控制（后者用于规避 hf-mirror 与 Xet 协议不兼容导致的 401）。

## 下载完成后启动

模型就绪后，回到仓库根目录执行：

```bash
docker compose up -d --build
docker compose ps        # 确认 whisper-svc 为 healthy
```