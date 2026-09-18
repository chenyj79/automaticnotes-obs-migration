$ErrorActionPreference = "Continue"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$repo = "mobiuslabsgmbh/faster-whisper-large-v3-turbo"
# 相对项目根目录，任意机器 clone 后直接执行即可
$modelDir = Join-Path $PSScriptRoot "whisper-models\models\mobiuslabsgmbh--faster-whisper-large-v3-turbo"
New-Item -ItemType Directory -Force -Path $modelDir | Out-Null

# 断点续传下载单个文件：循环直到 curl 成功
function Resume-Download($url, $dest) {
  for ($i = 1; $i -le 50; $i++) {
    Write-Output "[$i] 开始/续传: $dest (当前 $([math]::Round((Get-Item $dest -ErrorAction SilentlyContinue).Length/1MB,1)) MB)"
    curl.exe -L -C - --retry 5 --retry-delay 3 --connect-timeout 30 -o $dest $url 2>&1 | Select-Object -Last 1
    $code = $LASTEXITCODE
    if ($code -eq 0) {
      Write-Output "完成: $dest $([math]::Round((Get-Item $dest).Length/1MB,1)) MB"
      return $true
    }
    Write-Output "中断码=$code，3秒后重试..."
    Start-Sleep -Seconds 3
  }
  return $false
}

$base = "https://huggingface.co/$repo/resolve/main"
# faster-whisper large-v3-turbo 实际需要 5 个文件（model.bin + 4 个配置/词表文件）
$items = @(
  @{n="model.bin"; u="$base/model.bin?download=true"},
  @{n="config.json"; u="$base/config.json?download=true"},
  @{n="tokenizer.json"; u="$base/tokenizer.json?download=true"},
  @{n="preprocessor_config.json"; u="$base/preprocessor_config.json?download=true"},
  @{n="vocabulary.json"; u="$base/vocabulary.json?download=true"}
)
foreach ($it in $items) {
  $dest = Join-Path $modelDir $it.n
  $ok = Resume-Download $it.u $dest
  if (-not $ok) { Write-Output "最终失败: $($it.n)" }
}
Write-Output "ALL_DONE"