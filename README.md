# AutomaticNotes — AI 驱动的视频笔记平台

> 📝 上传视频 → AI 自动语音转写 → 智能生成结构化笔记 → 知识框架管理

AutomaticNotes 是一款基于 **Spring Boot 3.3** + **Vue 3** 的全栈应用，集成阿里云通义千问大模型 (DashScope) 和通义听悟 (Tingwu) 语音识别服务，帮助用户自动将视频内容转化为可编辑、可检索的结构化知识笔记。

---

## 📋 目录

[TOC]

---

## 项目简介

AutomaticNotes 解决的核心问题：**将视频/音频内容自动转化为结构化笔记知识**。

### 主要功能

- 🎥 **视频上传与管理** — 支持上传 mp4、avi、mov 等格式视频（最大 6GB）
- 🗣️ **AI 语音转写** — 通过阿里云通义听悟将视频音轨自动转为文字
- 🤖 **智能笔记生成** — 使用通义千问 (qwen-plus) 大模型将转写文本生成结构化笔记
- 📊 **视频评分分析** — AI 对视频内容进行多维度评分
- 📚 **知识框架管理** — 将多个视频笔记组织为知识框架，支持增量分析与汇总
- 🔍 **向量检索** — 基于 Redis Vector Store 的语义搜索能力
- 📤 **笔记导出** — 支持 PDF 导出
- 👤 **用户认证** — JWT Token 认证体系

---

## 技术栈

### 后端

| 技术                    | 说明                        |
| ---------------------- | --------------------------- |
| Spring Boot 3.3        | 基础框架                     |
| Spring AI Alibaba      | 大模型接入（DashScope 通义千问） |
| Spring Security + JWT  | 认证与鉴权                    |
| Spring Data JPA        | 数据持久层                    |
| MySQL 8                | 关系型数据库                   |
| Redis                  | 缓存与向量存储                 |
| RocketMQ               | 消息队列（异步视频处理）          |
| 阿里云 OSS              | 对象存储（视频/音频文件）         |
| 阿里云通义听悟 (Tingwu)   | 语音识别 ASR 服务              |
| FFmpeg                 | 音频提取                      |
| Java 17                | 运行环境                      |

### 前端

| 技术                  | 说明                     |
| -------------------- | ------------------------ |
| Vue 3                | 前端框架                  |
| Vite 7               | 构建工具                  |
| TypeScript           | 类型安全                  |
| Element Plus         | UI 组件库                 |
| Pinia                | 状态管理                  |
| Vue Router           | 路由管理                  |
| Axios                | HTTP 客户端               |
| md-editor-v3         | Markdown 编辑器           |
| KaTeX                | 数学公式渲染               |

---

## 核心交互流程

```
用户登录
  │
  ▼
仪表盘 (Dashboard) ── 数据概览、快速入口
  │
  ├─ 进入知识框架列表
  │    │
  │    ▼
  │  创建知识框架（如：「高等数学课程笔记」）
  │    │
  │    ▼
  │  进入知识框架详情页
  │    │
  │    ├─ 上传视频（在当前框架内）
  │    │    │
  │    │    ▼
  │    │  视频上传至 OSS → FFmpeg 提取音频时长
  │    │    │
  │    │    ▼
  │    │  通义听悟 ASR 语音转写（异步，RocketMQ 消息驱动）
  │    │    │
  │    │    ▼
  │    │  通义千问 AI 生成结构化笔记 + 多维评分
  │    │    │
  │    │    ▼
  │    │  点击视频 → 视频详情页查看/编辑笔记
  │    │
  │    └─ 触发 AI 增量分析 → 生成综合报告
  │
  └─ 个人中心
       │
       ▼
     查看/编辑个人信息
```

### 关键页面说明

| 页面               | 路由                  | 功能                                         |
| ------------------ | -------------------- | -------------------------------------------- |
| 登录页              | `/login`             | 用户注册 / 登录                                |
| 仪表盘              | `/dashboard`         | 数据概览、知识框架入口                           |
| 知识框架列表         | `/knowledge`         | 创建和管理知识框架                               |
| 知识框架详情         | `/knowledge/:id`     | 上传视频、查看框架下视频列表、触发 AI 增量分析  |
| 视频详情            | `/video/:id`         | 查看转写文本、AI 笔记、评分、支持笔记编辑与导出     |
| 个人中心            | `/profile`           | 查看与编辑个人信息                               |

---

## 项目结构

```
AutomaticNotes/
├── Dockerfile                      # 后端 Docker 镜像
├── pom.xml                         # Maven 项目配置
├── src/main/
│   ├── java/com/black/
│   │   ├── asr/                    # ASR 语音转写模块
│   │   ├── knowledge/              # 知识框架模块
│   │   ├── task/                   # 后台任务模块
│   │   ├── statistics/             # 统计模块
│   │   └── user/                   # 用户认证模块
│   └── resources/
│       ├── application.yml         # 开发环境配置
│       └── application-prod.yml    # 生产环境配置
├── frontend/
│   ├── Dockerfile                  # 前端 Docker 镜像
│   ├── nginx.conf                  # Nginx 反向代理配置
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── api/                    # API 接口定义
│       ├── views/                  # 页面组件
│       ├── layout/                 # 布局组件
│       ├── stores/                 # Pinia 状态管理
│       ├── router/                 # 路由配置
│       └── types/                  # TypeScript 类型定义
└── k8s/                            # Kubernetes 部署文件
    ├── namespace.yaml
    ├── secret.yaml
    ├── mysql.yaml
    ├── redis.yaml
    ├── rocketmq.yaml
    ├── backend.yaml
    └── frontend.yaml
```

---

## 环境准备

### 必要软件

| 软件        | 版本要求          | 用途                         |
| ----------- | ---------------- | --------------------------- |
| JDK         | 17+              | 后端运行环境                  |
| Maven       | 3.9+             | 后端构建工具                  |
| Node.js     | 20.19+ 或 22.12+ | 前端运行环境                  |
| npm         | 随 Node.js 安装   | 前端包管理器                  |
| MySQL       | 8.0+             | 数据库                       |
| Docker      | 最新版            | 部署 Redis Stack 和 RocketMQ |
| FFmpeg      | 最新版            | 音频提取                     |

### 基础服务安装

#### 1. MySQL

创建数据库（应用会通过 JPA 自动建表）：

```sql
CREATE DATABASE test_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### 2. Redis Stack（Docker 部署）

本项目使用 Redis 向量存储功能，需要 **redis-stack** 镜像（内置 RediSearch 模块）：

```bash
docker run -d \
  --name redis-stack \
  -p 6379:6379 \
  -p 8001:8001 \
  redis/redis-stack:latest
```

> 💡 端口 `8001` 为 RedisInsight 管理界面，可通过浏览器访问 `http://localhost:8001` 查看数据。

#### 3. RocketMQ（Docker 部署）

使用 Docker 部署 RocketMQ NameServer 和 Broker：

```bash
# 创建专用网络
docker network create rocketmq-net

# 启动 NameServer
docker run -d \
  --name rocketmq-namesrv \
  --network rocketmq-net \
  -p 9876:9876 \
  apache/rocketmq:5.3.1 \
  sh mqnamesrv

# 启动 Broker
docker run -d \
  --name rocketmq-broker \
  --network rocketmq-net \
  -p 10911:10911 \
  -p 10909:10909 \
  -e "NAMESRV_ADDR=rocketmq-namesrv:9876" \
  apache/rocketmq:5.3.1 \
  sh mqbroker -n rocketmq-namesrv:9876 
```

如果遇到问题请参考 https://rocketmq.apache.org/zh/docs/quickStart/02quickstartWithDocker/

> ⚠️ 确保 `ROCKETMQ_NAME_SERVER` 环境变量与你的部署地址一致（默认 `localhost:9876`）。

#### 4. FFmpeg

- **Windows**: 从 [ffmpeg.org](https://ffmpeg.org/download.html) 下载并添加到系统 PATH
- **macOS**: `brew install ffmpeg`
- **Linux**: `apt install ffmpeg` 或 `yum install ffmpeg`

---

## API Key 配置

本项目需要配置以下阿里云服务密钥，**推荐通过环境变量注入**，避免将密钥提交到代码仓库。

### 1. DashScope（通义千问 AI 大模型）— ⭐必需

用于 AI 笔记生成和视频评分。

- 前往 [DashScope 控制台](https://dashscope.console.aliyun.com/) 获取 API Key

```bash
export DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxx
```

### 2. 华为云 OBS（对象存储）— ⭐必需

用于存储上传的视频/音频文件。

- 在 [华为云 OBS 控制台](https://console.huaweicloud.com/console/?locale=zh-cn#/obs/manager/overview) 创建 Bucket
  - 存储策略建议选择"公共读"以便前端直接访问视频
  - 记录 Endpoint（如 `obs.cn-north-4.myhuaweicloud.com`）
- 在 [华为云 IAM 控制台](https://console.huaweicloud.com/console/?locale=zh-cn#/iam/users) 创建 AccessKey
- 前端采用预签名 URL 直传方式，无需额外 STS 配置
```bash
export HUAWEICLOUD_OBS_ACCESS_KEY_ID=your-access-key-id
export HUAWEICLOUD_OBS_ACCESS_KEY_SECRET=your-access-key-secret
export HUAWEICLOUD_OBS_BUCKET_NAME=your-bucket-name
export HUAWEICLOUD_OBS_ENDPOINT=obs.cn-north-4.myhuaweicloud.com
```

### 3. 阿里云通义听悟（ASR 语音识别）— ⭐必需

用于视频语音转文字。

- 前往 [通义听悟控制台](https://nls-portal.console.aliyun.com/tingwu/overview?spm=a2c4g.11186623.0.0.64712be1mEqC3M) 创建项目并获取 AppKey

```bash
export ASR_ALIYUN_ACCESS_KEY_ID=your-access-key-id
export ASR_ALIYUN_ACCESS_KEY_SECRET=your-access-key-secret
export ASR_ALIYUN_APP_KEY=your-tingwu-app-key
```

### 4. 其它配置（可选）

```bash
# MySQL 密码
export MYSQL_PASSWORD=your-mysql-password

# JWT 密钥（不设置会使用默认值）
export JWT_SECRET=your-jwt-secret-base64

# Redis（不设置默认为 localhost:6379）
export REDIS_HOST=localhost
export REDIS_PORT=6379

# RocketMQ NameServer 地址
export ROCKETMQ_NAME_SERVER=localhost:9876
```

### 环境变量速查表

| 环境变量                            | 必需 | 说明                    |
|---------------------------------|---|-----------------------|
| `DASHSCOPE_API_KEY`                    | ✅ | 通义千问 API Key            |
| `HUAWEICLOUD_OBS_ACCESS_KEY_ID`       | ✅ | 华为云 OBS AccessKey ID      |
| `HUAWEICLOUD_OBS_ACCESS_KEY_SECRET`   | ✅ | 华为云 OBS AccessKey Secret  |
| `HUAWEICLOUD_OBS_BUCKET_NAME`         | ✅ | 华为云 OBS Bucket 名称        |
| `HUAWEICLOUD_OBS_ENDPOINT`            | ✅ | 华为云 OBS 终端节点            |
| `ASR_ALIYUN_ACCESS_KEY_ID`            | ✅ | 听悟 AccessKey ID         |
| `ASR_ALIYUN_ACCESS_KEY_SECRET`        | ✅ | 听悟 AccessKey Secret     |
| `ASR_ALIYUN_APP_KEY`                  | ✅ | 听悟 AppKey              |
| `MYSQL_PASSWORD`                      | ⬚ | MySQL root 密码           |
| `JWT_SECRET`                          | ⬚ | JWT 签名密钥                |
| `REDIS_HOST`                          | ⬚ | Redis 主机地址              |
| `REDIS_PORT`                          | ⬚ | Redis 端口                |
| `ROCKETMQ_NAME_SERVER`                | ⬚ | RocketMQ NameServer 地址   |

---

## 本地开发环境搭建

### 1. 克隆项目

```bash
git clone <your-repo-url>
cd AutomaticNotes
```

### 2. 配置环境变量

参考上一节，设置所有必需的环境变量。你也可以直接编辑 `src/main/resources/application.yml` 中的默认值（**不推荐提交到 Git**）。

### 3. 启动后端

```bash
# 在项目根目录
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`。

> 💡 首次启动时 JPA 会自动在 MySQL 中创建所需的表结构。

### 4. 启动前端

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端默认运行在 `http://localhost:3000`，已配置代理将 `/api` 请求转发至后端 `localhost:8080`。

### 5. 访问应用

浏览器打开 `http://localhost:3000`，注册账号后即可开始使用。

---

### 6. 如果创建关于股票相关的知识框架，按下面操作进行：

- 进行种类划分：左侧、右侧、操作、股票
  - 左侧：用于归类基于基本面和估值的低位布局知识点，强调股价处于下行或低位区间，通常低于合理估值或低于阶段性高低点中枢，关注 EPS、PE、PB、现金流、财务质量、安全边际，以及分批建仓、蛛网交易、十年回本法、相对/绝对估值等内容。
  - 右侧：用于归类基于趋势和行情强弱的顺势交易知识点，强调股价已走出上升通道或突破走势，关注高于估值后的价差机会，重点包括阶梯战法、镜射理论、突破/回拉买点、RSI/KD、量价关系、背离判断、止损止盈等内容。
  - 操作：用于归类具体执行方法和交易动作，即如何买、如何卖、如何加减仓、如何止损、如何筛选、如何观察和机械化执行的内容。重点是操作步骤、仓位管理、纪律规则和实盘执行细节，而不是某个股票本身或某个宏观判断。
  - 股票：用于归类具体股票本身的信息和案例，包括个股名称、公司特点、行业属性、财报表现、估值位置、走势形态以及围绕某只股票展开的教学分析案例。这个分类强调“某一只具体股票”或“具体标的案例”，而不是通用策略。

- 进行初始知识点构建：根据src/main/resources/stockFile目录构建初始知识点，使用前端界面可以直接向量化

## 常见问题

### Q: 后端启动报数据库连接错误？

确认 MySQL 已启动，数据库 `test_db` 已创建，并且 `MYSQL_PASSWORD` 环境变量已设置。

### Q: 视频上传后没有开始转写？

检查：
1. RocketMQ 是否正常运行
2. OSS 配置是否正确（视频需先上传至 OSS）
3. 通义听悟 AppKey 和 AccessKey 是否配置
4. 服务器是否安装了 FFmpeg

### Q: 前端 API 请求返回 404？

开发模式下确保后端运行在 `localhost:8080`。Vite 配置中已设置 `/api` 代理到 `http://localhost:8080`。

### Q: 如何修改 AI 大模型？

在 `application.yml` 中修改 `spring.ai.dashscope.chat.options.model`，支持的模型列表参考 [DashScope 模型列表](https://help.aliyun.com/zh/model-studio/getting-started/models)。

