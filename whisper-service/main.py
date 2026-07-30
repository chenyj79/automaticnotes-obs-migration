import os
import sys
import re
import asyncio
from concurrent.futures import ThreadPoolExecutor
import threading

# 解决 Windows 环境下多个包带来的 OpenMP 冲突报错：OMP: Error #15
os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

# 解决 Windows 平台 CTranslate2 找不到 CUDA dll (例如 cublas64_12.dll) 的问题
if sys.platform == "win32":
    import site

    for sp in site.getsitepackages():
        cuda_paths = [
            os.path.join(sp, "nvidia", "cublas", "bin"),
            os.path.join(sp, "nvidia", "cudnn", "bin"),
            os.path.join(sp, "torch", "lib")
        ]
        for p in cuda_paths:
            if os.path.exists(p):
                os.environ["PATH"] = p + os.pathsep + os.environ["PATH"]
                if hasattr(os, 'add_dll_directory'):
                    os.add_dll_directory(p)

import requests
import tempfile
import subprocess
from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from faster_whisper import WhisperModel, BatchedInferencePipeline
import uvicorn
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ==================== 生产环境模型配置 ====================
MODEL_SIZE = os.getenv("WHISPER_MODEL", "large-v3")
# 24GB 显存充裕，使用 float16 获取最佳精度
COMPUTE_TYPE = os.getenv("COMPUTE_TYPE", "float16")
DEVICE = os.getenv("DEVICE", "cuda")
# 下载文件超时时间（秒）
DOWNLOAD_TIMEOUT = int(os.getenv("DOWNLOAD_TIMEOUT", "600"))
# 24GB 显存可以支撑更大的 batch
BATCH_SIZE = int(os.getenv("BATCH_SIZE", "24"))
# GPU 设备列表，逗号分隔，例如 "0,1" 表示使用两张卡
GPU_DEVICES = os.getenv("GPU_DEVICES", "0,1")


# ==================== 本地环境模型配置 ====================
# MODEL_SIZE = os.getenv("WHISPER_MODEL", "large-v3-turbo")
# # 采用 int8_float16 模型量化技术能够进一步降低显存并且依靠 Tensor Core 加速性能
# COMPUTE_TYPE = os.getenv("COMPUTE_TYPE", "int8_float16")
# DEVICE = os.getenv("DEVICE", "cuda")
# # 下载文件超时时间（秒）
# DOWNLOAD_TIMEOUT = int(os.getenv("DOWNLOAD_TIMEOUT", "600"))
# BATCH_SIZE = int(os.getenv("BATCH_SIZE", "16"))
# # GPU 设备列表，逗号分隔，例如 "0,1" 表示使用两张卡
#GPU_DEVICES = os.getenv("GPU_DEVICES", "0")

LOCAL_MODEL_DIR = os.path.join(os.path.dirname(__file__), "models")

# ==================== 解析 GPU 设备列表 ====================
gpu_device_ids = [int(d.strip()) for d in GPU_DEVICES.split(",") if d.strip()]
if not gpu_device_ids:
    gpu_device_ids = [0]
logger.info(f"配置的 GPU 设备列表: {gpu_device_ids}")


@asynccontextmanager
async def lifespan(application: FastAPI):
    """FastAPI lifespan 事件：启动时初始化 Worker 队列，关闭时清理资源。"""
    global worker_queue
    worker_queue = asyncio.Queue()
    for w in gpu_workers:
        await worker_queue.put(w)
    logger.info(f"Worker 调度队列初始化完成，可用 Worker 数: {worker_queue.qsize()}")
    yield
    logger.info("服务正在关闭...")


app = FastAPI(title="Whisper ASR Service", lifespan=lifespan)


# ==================== 标点恢复模型加载 ====================
punc_model = None
try:
    from funasr import AutoModel as FunASRAutoModel
    # ct-punc 模型小（~300MB），放 GPU 上推理更快；与 Whisper 不存在时序冲突
    _punc_device = "cuda:0" if DEVICE == "cuda" else "cpu"
    logger.info(f"正在加载 FunASR ct-punc 标点恢复模型 ({_punc_device})...")
    punc_model = FunASRAutoModel(model="ct-punc", device=_punc_device)
    logger.info("FunASR ct-punc 标点恢复模型加载完成。")
except Exception as e:
    logger.warning(f"FunASR ct-punc 模型加载失败，将跳过标点恢复: {e}")
    punc_model = None

# punc_model 是全局共享的，多个 GPU Worker 线程可能并发调用，
# PyTorch GPU 推理非线程安全，需要加锁保护
_punc_lock = threading.Lock()

# ==================== 繁简转换 ====================
cc_converter = None
try:
    import opencc
    cc_converter = opencc.OpenCC('t2s')  # 繁体转简体
    logger.info("繁简转换器 (OpenCC t2s) 初始化完成。")
except Exception as e:
    logger.warning(f"OpenCC 加载失败，将跳过繁简转换: {e}")
    cc_converter = None


# ==================== GPU Worker 类 ====================
class GPUWorker:
    """
    封装单个 GPU 上的 Whisper 模型实例。
    每个 Worker 绑定一张显卡，拥有独立的推理锁。
    """

    def __init__(self, device_index: int):
        self.device_index = device_index
        self.lock = threading.Lock()
        self.model = None
        self.pipeline = None
        self._load_model()

    def _load_model(self):
        logger.info(f"[GPU {self.device_index}] 正在加载 Whisper 模型: {MODEL_SIZE}, "
                     f"计算类型: {COMPUTE_TYPE}...")
        try:
            base_model = WhisperModel(
                MODEL_SIZE,
                device=DEVICE,
                device_index=self.device_index,
                compute_type=COMPUTE_TYPE,
                num_workers=1,
                download_root=LOCAL_MODEL_DIR
            )
            self.pipeline = BatchedInferencePipeline(model=base_model)
            self.model = base_model
            logger.info(f"[GPU {self.device_index}] 模型加载完成。")
        except Exception as e:
            logger.error(f"[GPU {self.device_index}] 模型加载失败: {e}")
            raise

    def transcribe(self, file_path: str) -> list:
        """
        在该 GPU 上执行转录推理，加锁确保串行化。
        返回原始 segment 列表，每个 segment 包含 word-level timestamps。
        """
        with self.lock:
            try:
                return self._run_transcribe_loop(file_path, use_batch=True)
            except Exception as e:
                error_msg = str(e).lower()
                # 捕获 CTranslate2 批处理模式下偶尔出现的 CUDA 异常 (如 invalid argument)
                if "cuda" in error_msg or "invalid argument" in error_msg or "cublas" in error_msg:
                    logger.warning(f"[GPU {self.device_index}] Batch 模式推理发生异常: {e}。由于 faster-whisper 批处理与 word_timestamps 组合的已知 bug，现在降级为串行(Non-Batch)模式重试...")
                    return self._run_transcribe_loop(file_path, use_batch=False)
                else:
                    raise

    def _run_transcribe_loop(self, file_path: str, use_batch: bool) -> list:
        logger.info(f"[GPU {self.device_index}] 开始推理 (Batch={use_batch}): {file_path}")
        
        kwargs = dict(
            language='zh',
            beam_size=5,
                # 启用 word-level timestamps，用于后续基于标点的精确重分段
            word_timestamps=True,
                # 禁止连续重复 4-gram，防止解码器陷入重复循环幻觉
            no_repeat_ngram_size=4,
                # 不再使用 initial_prompt，依赖 ct-punc 做后处理标点恢复
                # 启用 VAD 过滤静音段，避免幻觉
            vad_filter=True,
            vad_parameters=dict(
                    min_silence_duration_ms=500,  # 最短静音时长（用于切分语音段）
                    speech_pad_ms=200,             # 语音段两端填充
            ),
                # 关闭基于前文的条件推理，防止错误传播和长段无标点
            condition_on_previous_text=False,
        )

        if use_batch:
            segments_generator, info = self.pipeline.transcribe(
                file_path,
                batch_size=BATCH_SIZE,
                **kwargs
            )
        else:
            segments_generator, info = self.model.transcribe(
                file_path,
                **kwargs
            )

        logger.info(f"[GPU {self.device_index}] 检测到语言 '{info.language}'，概率: {info.language_probability:.4f}")

        # 收集所有 segment，包含 word-level timestamps
        # 注意：生成器在被迭代时才会真正执行 GPU 计算并可能抛出 CUDA 异常
        raw_segments = []
        for seg in segments_generator:
            text = seg.text.strip()
            if text:
                words = []
                if seg.words:
                    for w in seg.words:
                        word_text = w.word.strip()
                        if word_text:
                            words.append({
                                "word": word_text,
                                "start": w.start,
                                "end": w.end,
                            })
                raw_segments.append({
                    "start": seg.start,
                    "end": seg.end,
                    "text": text,
                    "words": words,
                })

        logger.info(f"[GPU {self.device_index}] 推理完成，原始 segment 数: {len(raw_segments)}")
        return raw_segments


# ==================== 初始化 GPU Workers ====================
gpu_workers: list[GPUWorker] = []

if DEVICE == "cuda":
    for dev_id in gpu_device_ids:
        try:
            worker = GPUWorker(device_index=dev_id)
            gpu_workers.append(worker)
        except Exception as e:
            logger.error(f"GPU {dev_id} 初始化失败，跳过: {e}")

# 如果没有任何 GPU 成功加载，回退到 CPU
if not gpu_workers:
    logger.warning("没有可用的 GPU Worker，回退到 CPU 模式...")
    try:
        cpu_base = WhisperModel(
            MODEL_SIZE,
            device="cpu",
            compute_type="int8",
            cpu_threads=4,
            num_workers=1,
            download_root=LOCAL_MODEL_DIR
        )
        cpu_worker = GPUWorker.__new__(GPUWorker)
        cpu_worker.device_index = -1
        cpu_worker.lock = threading.Lock()
        cpu_worker.model = cpu_base
        cpu_worker.pipeline = BatchedInferencePipeline(model=cpu_base)
        gpu_workers.append(cpu_worker)
        logger.info("CPU 模型加载完成。")
    except Exception as e:
        logger.error(f"CPU 模式加载也失败了: {e}")
        raise RuntimeError("无法加载任何 Whisper 模型，服务无法启动。")

logger.info(f"共初始化 {len(gpu_workers)} 个推理 Worker。")

# ==================== Worker 调度队列 ====================
# 使用 asyncio.Queue 实现简单的负载均衡：
# 所有空闲 Worker 放入队列，请求到达时取出一个，推理完毕后归还。
worker_queue: asyncio.Queue = None

# 线程池：将阻塞的推理操作放入线程池，避免阻塞 FastAPI 事件循环
_executor = ThreadPoolExecutor(max_workers=len(gpu_workers))


# ==================== 数据模型 ====================
class TranscribeRequest(BaseModel):
    id: int = None
    url: str = None


# ==================== 健康检查 ====================
@app.get("/health")
async def health_check():
    """健康检查接口，供探针使用"""
    return {
        "status": "ok",
        "model": MODEL_SIZE,
        "device": DEVICE,
        "compute_type": COMPUTE_TYPE,
        "gpu_workers": len(gpu_workers),
        "gpu_devices": [w.device_index for w in gpu_workers],
        "punc_model": "ct-punc" if punc_model else "disabled",
    }


# ==================== 工具函数 ====================
def download_file(url: str, output_path: str):
    """下载远程文件到本地，带超时保护"""
    logger.info(f"正在下载文件: {url}")
    response = requests.get(url, stream=True, timeout=(30, DOWNLOAD_TIMEOUT))
    if response.status_code == 200:
        with open(output_path, 'wb') as f:
            for chunk in response.iter_content(chunk_size=8192):
                f.write(chunk)
    else:
        raise Exception(f"文件下载失败，HTTP 状态码: {response.status_code}")


def validate_video_file(video_path: str):
    """使用 ffprobe 深度校验文件格式，确保包含音频轨和视频轨"""
    logger.info(f"正在进行深度格式校验: {video_path}")
    command = [
        "ffprobe",
        "-v", "error",
        "-show_entries", "stream=codec_type",
        "-of", "default=nw=1:nk=1",
        video_path
    ]
    try:
        result = subprocess.run(command, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        streams = result.stdout.strip().split('\n')
        if "audio" not in streams:
            logger.error("文件格式校验失败：未检测到音频流。")
            raise Exception("该视频无声音/无音频轨，无法进行语音转写。")
        if "video" not in streams:
            logger.warning("文件格式校验：未检测到视频流，可能是一个纯音频文件。")
    except subprocess.CalledProcessError as e:
        logger.error(f"ffprobe 校验失败: {e.stderr}")
        raise Exception("文件格式校验失败：文件损坏或格式不受支持。")


def extract_audio_from_video(video_path: str, audio_path: str):
    """使用 ffmpeg 从视频中提取 16kHz 单声道音频"""
    logger.info(f"正在从视频提取音频: {video_path} -> {audio_path}")
    command = [
        "ffmpeg",
        "-y",               # 覆盖输出
        "-i", video_path,   # 输入文件
        "-vn",              # 去除视频
        "-acodec", "pcm_s16le", # 输出 16-bit PCM
        "-ar", "16000",     # 16kHz 采样率
        "-ac", "1",         # 单声道
        audio_path
    ]
    try:
        subprocess.run(command, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    except subprocess.CalledProcessError as e:
        logger.error(f"提取音频失败: {e.stderr.decode('utf-8', errors='ignore')}")
        raise Exception("音频提取失败，请检查输入文件格式")


# ==================== 重复折叠（反幻觉） ====================

# 匹配 1~4 个字符连续重复超过 4 次的模式
_REPEAT_PATTERN = re.compile(r'(.{1,4})\1{4,}')
# 最多保留的重复次数
_MAX_REPEATS = 4


def _collapse_repetitions(segments: list) -> list:
    """
    检测并折叠 Whisper 重复循环幻觉。

    Whisper 的自回归解码器在遇到韵律单一的音频时可能陷入重复循环，
    产出 "慢慢慢慢..."（上百次）这样的幻觉文本。

    本函数在每个 segment 内：
    1. 用正则检测连续重复 >_MAX_REPEATS 次的字符/短语
    2. 折叠为最多 _MAX_REPEATS 次
    3. 同步清理多余的 word timestamps（幻觉 word 也是重复的）
    """
    cleaned_segments = []

    for seg in segments:
        original_text = seg["text"]
        cleaned_text = _REPEAT_PATTERN.sub(
            lambda m: m.group(1) * _MAX_REPEATS, original_text
        )

        # 文本没变化，直接保留
        if cleaned_text == original_text:
            cleaned_segments.append(seg)
            continue

        logger.info(f"重复折叠: {len(original_text)} -> {len(cleaned_text)} 字符")

        # 同步清理 words：重建 word 列表，跳过重复幻觉产生的多余 word
        words = seg.get("words", [])
        if words:
            words = _clean_repeated_words(words, _MAX_REPEATS)

        cleaned_seg = dict(seg)
        cleaned_seg["text"] = cleaned_text
        cleaned_seg["words"] = words
        # 更新 segment 时间边界
        if words:
            cleaned_seg["start"] = words[0]["start"]
            cleaned_seg["end"] = words[-1]["end"]
        cleaned_segments.append(cleaned_seg)

    return cleaned_segments


def _clean_repeated_words(words: list, max_repeats: int) -> list:
    """
    清理重复幻觉产生的多余 word。

    策略：检测连续相同的 word.word 文本，如果连续出现超过 max_repeats 次，
    只保留前 max_repeats 个。
    """
    if not words:
        return words

    cleaned = []
    streak_count = 0
    prev_text = None

    for w in words:
        curr_text = w["word"].strip()
        if curr_text == prev_text:
            streak_count += 1
        else:
            streak_count = 1
            prev_text = curr_text

        if streak_count <= max_repeats:
            cleaned.append(w)

    return cleaned


# ==================== 标点恢复与重分段 ====================

# ct-punc 可能插入的标点符号集合
_PUNC_CHARS = set('，。！？、；：,.:;!?""\'\'""''…—–－-·～~()（）《》「」『』【】〔〕')

# 终结标点：表示一个完整句子结束
_TERMINAL_CHARS = {'。', '！', '？', '.', '!', '?', '；', ';'}


def _is_content_char(ch: str) -> bool:
    """
    判断是否为「内容字符」—— 即 ct-punc 不会增删、只会保留的字符。
    内容字符 = 非空白 且 非标点。包括：汉字、字母、数字等。

    ct-punc 可能对内容字符做的唯一改动是英文大小写变化（如 anyway -> Anyway），
    但不会增加或删除内容字符，也不会改变它们的顺序。
    """
    if ch.isspace():
        return False
    if ch in _PUNC_CHARS:
        return False
    return True


def _restore_punctuation(full_text: str) -> str:
    """
    使用 ct-punc 对完整文本进行一次性标点恢复，返回带标点的文本。
    如果模型不可用或出错则原样返回。
    """
    if not punc_model or not full_text.strip():
        return full_text

    with _punc_lock:
        try:
            result = punc_model.generate(input=full_text)
            if result and isinstance(result, list) and len(result) > 0:
                restored = result[0].get("text", full_text)
                return restored if restored else full_text
            return full_text
        except Exception as e:
            logger.warning(f"标点恢复异常，返回原文: {e}")
            return full_text


def resegment_by_punctuation(
    raw_segments: list,
    target_duration_ms: int = 45000,
    max_gap_ms: int = 2000,
) -> list:
    """
    基于 word-level timestamps + ct-punc 标点的精确重分段算法。

    核心思想：
    完全抛弃 VAD 产生的原始 segment 边界（它们可能切在句子中间），
    改为用 ct-punc 恢复的句子边界（终结标点）作为分段依据，
    用 word-level timestamps 获取每个句子的精确起止时间。

    流程：
    1. 提取全局 word 列表（将所有 segment 的 words 拍平、排序）
    2. 拼接全文文本 → ct-punc 全文标点恢复
    3. 遍历标点恢复后的文本，逐内容字符消费 word 列表，在终结标点处切分为句子
    4. 将相邻句子按 target_duration 合并为段落

    Args:
        raw_segments: Whisper 原始 segments（含 words 字段）
        target_duration_ms: 目标段落时长（毫秒），每段大约这个长度
        max_gap_ms: 两句话之间的最大静音间隔，超过则强制分段
    """
    if not raw_segments:
        return []

    # ---- Step 1: 构建全局有序 word 列表 ----
    all_words = []
    for seg in raw_segments:
        for w in seg.get("words", []):
            all_words.append(w)

    # 如果没有 word-level 数据，回退到基于 segment 的简单合并
    if not all_words:
        logger.warning("没有 word-level timestamps，回退到基于 segment 边界的合并。")
        return _fallback_merge(raw_segments, target_duration_ms, max_gap_ms)

    # ---- Step 2: 拼接全文文本 + ct-punc 标点恢复 ----
    full_text = "".join(seg["text"] for seg in raw_segments)
    punctuated_text = _restore_punctuation(full_text)

    logger.info(f"全文长度: {len(full_text)} 字符, 标点恢复后: {len(punctuated_text)} 字符, "
                f"word 总数: {len(all_words)}")

    # ---- Step 3: 按 word 粒度遍历，在终结标点处切分为句子 ----
    #
    # 关键洞察：ct-punc 只在 word 和 word 之间插入标点，不会插到一个 word 内部。
    # 因此我们以 word 为单位消费 punctuated_text：
    #   1. 消费当前 word 的所有内容字符（跳过中间可能夹杂的非终结标点/空格）
    #   2. 消费 word 后面紧跟的所有非内容字符（标点、空格）
    #   3. 如果这些非内容字符中包含终结标点，则切分一个句子

    sentences = []          # 最终产出的句子列表 [{start, end, text}]
    sent_words = []         # 当前句子包含的 word 列表
    sent_text_parts = []    # 当前句子的文本片段（含标点）
    punc_idx = 0            # punctuated_text 的遍历指针
    punc_len = len(punctuated_text)

    for word in all_words:
        word_content_count = sum(1 for c in word["word"] if _is_content_char(c))
        consumed = 0

        # 消费该 word 的所有内容字符
        while consumed < word_content_count and punc_idx < punc_len:
            ch = punctuated_text[punc_idx]
            punc_idx += 1
            sent_text_parts.append(ch)
            if _is_content_char(ch):
                consumed += 1

        sent_words.append(word)

        # 消费 word 后面的非内容字符（标点、空格），并检测是否有终结标点
        has_terminal = False
        while punc_idx < punc_len and not _is_content_char(punctuated_text[punc_idx]):
            ch = punctuated_text[punc_idx]
            punc_idx += 1
            sent_text_parts.append(ch)
            if ch in _TERMINAL_CHARS:
                has_terminal = True

        # 遇到终结标点 → 切分一个句子
        if has_terminal and sent_words:
            sent_text = "".join(sent_text_parts).strip()
            if sent_text:
                sentences.append({
                    "start": sent_words[0]["start"],
                    "end": sent_words[-1]["end"],
                    "text": sent_text,
                })
            sent_words = []
            sent_text_parts = []

    # 处理剩余文本（最后一段没有终结标点的尾部）
    if sent_words:
        remaining_text = "".join(sent_text_parts).strip()
        if remaining_text:
            sentences.append({
                "start": sent_words[0]["start"],
                "end": sent_words[-1]["end"],
                "text": remaining_text,
            })

    logger.info(f"基于标点切分为 {len(sentences)} 个句子。")

    # ---- Step 4: 将句子按 target_duration 合并为段落 ----
    return _merge_sentences_to_paragraphs(sentences, target_duration_ms, max_gap_ms)


def _merge_sentences_to_paragraphs(
    sentences: list,
    target_duration_ms: int = 45000,
    max_gap_ms: int = 2000,
) -> list:
    """
    将句子合并为段落。
    每个句子已经由终结标点精确切分，因此段落的边界一定在完整句末尾。

    合并规则：
    1. 持续拼接相邻句子
    2. 当累计时长 >= target_duration_ms 时，在当前句子末尾断开
    3. 当两句间隔 >= max_gap_ms 时，在上一句末尾断开
    """
    if not sentences:
        return []

    paragraphs = []
    curr = None

    for sent in sentences:
        start_ms = int(sent["start"] * 1000)
        end_ms = int(sent["end"] * 1000)
        text = sent["text"]

        if not text:
            continue

        if curr is None:
            curr = {"start": start_ms, "end": end_ms, "text": text}
            continue

        gap = start_ms - curr["end"]
        duration = curr["end"] - curr["start"]

        # 判断是否在此句边界断开段落
        should_break = False
        if duration >= target_duration_ms:
            should_break = True
        elif gap >= max_gap_ms:
            should_break = True

        if should_break:
            paragraphs.append(curr)
            curr = {"start": start_ms, "end": end_ms, "text": text}
        else:
            # 智能拼接：中英文交界无空格、英文相连补空格
            if curr["text"] and text:
                last_c = curr["text"][-1]
                first_c = text[0]
                if last_c.isascii() and last_c.isalnum() and first_c.isascii() and first_c.isalnum():
                    curr["text"] += " " + text
                else:
                    curr["text"] += text
            curr["end"] = end_ms

    if curr is not None:
        paragraphs.append(curr)

    return paragraphs


def _fallback_merge(
    segments: list,
    target_duration_ms: int = 45000,
    max_gap_ms: int = 2000,
) -> list:
    """
    回退方案：当没有 word-level timestamps 时，使用原始 segment 边界进行简单合并。
    先做全文标点恢复（映射回 segment），再按标点和时长合并。
    """
    HARD_LIMIT_FACTOR = 3.5

    # 全文标点恢复并映射回 segment
    segments = _restore_punc_and_map_back(segments)

    merged = []
    curr = None

    for seg in segments:
        text = seg["text"]
        start_ms = int(seg["start"] * 1000)
        end_ms = int(seg["end"] * 1000)

        if not text:
            continue

        if curr is None:
            curr = {"start": start_ms, "end": end_ms, "text": text}
            continue

        gap = start_ms - curr["end"]
        duration = curr["end"] - curr["start"]
        last_char = curr["text"][-1] if curr["text"] else ''
        is_terminal = last_char in _TERMINAL_CHARS
        projected_duration = end_ms - curr["start"]

        should_break = False

        # 规则 1: 超过目标时长 + 末尾有终结标点 → 正常断开
        if duration >= target_duration_ms and is_terminal:
            should_break = True
        # 规则 2: 两段间隔过大 + 末尾有终结标点 → 正常断开
        elif gap >= max_gap_ms and is_terminal:
            should_break = True
        # 规则 3: 硬上限兜底 — 超过 3.5 倍仍无终结标点，强制断开
        elif projected_duration > target_duration_ms * HARD_LIMIT_FACTOR:
            should_break = True

        if should_break:
            merged.append(curr)
            curr = {"start": start_ms, "end": end_ms, "text": text}
        else:
            # 智能拼接：中英文交界无空格、英文相连补空格
            if curr["text"] and text:
                last_c = curr["text"][-1]
                first_c = text[0]
                if last_c.isascii() and last_c.isalnum() and first_c.isascii() and first_c.isalnum():
                    curr["text"] += " " + text
                else:
                    curr["text"] += text
            curr["end"] = end_ms

    if curr is not None:
        merged.append(curr)

    return merged


def _restore_punc_and_map_back(segments: list) -> list:
    """
    全文标点恢复后按内容字符偏移映射回各 segment（回退方案专用）。
    """
    if not punc_model or not segments:
        return segments

    seg_content_counts = []
    full_text_parts = []
    for seg in segments:
        text = seg["text"].strip()
        content_count = sum(1 for ch in text if _is_content_char(ch))
        seg_content_counts.append(content_count)
        full_text_parts.append(text)

    full_text = "".join(full_text_parts)
    if not full_text:
        return segments

    punctuated_text = _restore_punctuation(full_text)
    if punctuated_text == full_text:
        return segments

    restored_segments = []
    punc_idx = 0
    punc_len = len(punctuated_text)

    for i, content_count in enumerate(seg_content_counts):
        while punc_idx < punc_len and punctuated_text[punc_idx].isspace():
            punc_idx += 1

        consumed = 0
        seg_start = punc_idx

        while consumed < content_count and punc_idx < punc_len:
            ch = punctuated_text[punc_idx]
            punc_idx += 1
            if _is_content_char(ch):
                consumed += 1

        while punc_idx < punc_len and punctuated_text[punc_idx] in _PUNC_CHARS:
            punc_idx += 1

        seg_text = punctuated_text[seg_start:punc_idx].strip()
        restored_seg = dict(segments[i])
        restored_seg["text"] = seg_text if seg_text else segments[i]["text"]
        restored_segments.append(restored_seg)

    return restored_segments


# ==================== 核心推理流程 ====================
def _do_transcribe(worker: GPUWorker, file_path: str) -> dict:
    """
    完整的推理流程：
    Whisper 转录(word timestamps) -> 全文标点恢复 -> 基于标点+word时间戳精确重分段。
    在线程池中执行，通过 worker 绑定的锁确保单卡串行化。
    """
    # Step 1: Whisper 转录（VAD 分段 + batch 推理 + word timestamps）
    raw_segments = worker.transcribe(file_path)

    if not raw_segments:
        return {
            "code": 200,
            "message": "success",
            "data": []
        }

    # Step 2: 折叠重复循环幻觉
    raw_segments = _collapse_repetitions(raw_segments)

    # Step 3+4: 全文标点恢复 + 基于标点的精确重分段
    total_words = sum(len(seg.get("words", [])) for seg in raw_segments)
    logger.info(f"[GPU {worker.device_index}] 开始标点恢复与重分段，"
                f"共 {len(raw_segments)} 个 segment, {total_words} 个 word...")

    result_segments = resegment_by_punctuation(raw_segments)

    logger.info(f"[GPU {worker.device_index}] 重分段完成，最终 {len(result_segments)} 个段落。")

    # Step 5: 繁体转简体
    if cc_converter:
        for seg in result_segments:
            seg["text"] = cc_converter.convert(seg["text"])

    return {
        "code": 200,
        "message": "success",
        "data": result_segments
    }


class PuncRequest(BaseModel):
    text: str


# ==================== 转写接口 ====================
@app.post("/transcribe")
async def transcribe(request: TranscribeRequest):
    if not request.url:
        raise HTTPException(status_code=400, detail="参数错误：url 不能为空")

    # 临时文件用于保存下载的视频和提取的音频
    video_fd, temp_video_path = tempfile.mkstemp(suffix=".tmp")
    os.close(video_fd)
    audio_fd, temp_audio_path = tempfile.mkstemp(suffix=".wav")
    os.close(audio_fd)

    # 从队列获取一个空闲的 GPU Worker
    worker = await worker_queue.get()
    logger.info(f"分配 GPU Worker {worker.device_index} 处理请求 (id={request.id})")

    try:
        download_file(request.url, temp_video_path)
        logger.info(f"文件下载完成，保存至 {temp_video_path}，准备校验...")

        validate_video_file(temp_video_path)
        logger.info("文件校验通过，准备提取音频...")

        extract_audio_from_video(temp_video_path, temp_audio_path)
        logger.info("音频提取完成，开始推理...")

        # 将阻塞的推理操作放到线程池中执行，避免阻塞 FastAPI 事件循环
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(
            _executor, _do_transcribe, worker, temp_audio_path
        )
        return result

    except Exception as e:
        logger.error(f"转录过程中发生异常: {e}")
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        # 归还 Worker 到队列
        await worker_queue.put(worker)
        logger.info(f"GPU Worker {worker.device_index} 已归还到队列。")
        # 清理临时文件
        if os.path.exists(temp_video_path):
            os.remove(temp_video_path)
        if os.path.exists(temp_audio_path):
            os.remove(temp_audio_path)


# ==================== 标点恢复接口 ====================
@app.post("/restore_punc")
async def restore_punc(request: PuncRequest):
    """
    提供独立的标点恢复接口，主要用于标点模型的孤立评估（Isolated Evaluation）。
    """
    if not request.text:
        return {"code": 200, "message": "success", "data": ""}

    if not punc_model:
        logger.warning("标点模型未加载，将原样返回文本。")
        return {"code": 200, "message": "model not loaded", "data": request.text}

    logger.info(f"收到标点恢复请求，文本长度: {len(request.text)}")
    restored = _restore_punctuation(request.text)
    return {"code": 200, "message": "success", "data": restored}


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
