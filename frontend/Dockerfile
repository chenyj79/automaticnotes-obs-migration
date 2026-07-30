# ===== Stage 1: Node 构建 =====
FROM node:22-alpine AS builder

WORKDIR /app

# 先复制 package 文件单独安装依赖（利用 Docker 缓存）
COPY package.json package-lock.json ./
RUN npm ci

# 复制源码并构建
COPY . .
RUN npm run build-only

# ===== Stage 2: Nginx 托管 =====
FROM nginx:alpine

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制自定义 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
