# ===== Stage 1: Maven 编译 =====
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 先复制 pom.xml 单独下载依赖（利用 Docker 缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并打包
COPY src ./src
RUN mvn package -DskipTests -B

# ===== Stage 2: 运行环境 =====
FROM eclipse-temurin:17-jre-alpine

# 安装 ffmpeg（项目依赖）
# Alpine 官方源 dl-cdn.alpinelinux.org 在国内被墙/DNS 解析失败，改用阿里云镜像源（保留镜像自带的版本路径）
RUN sed -i 's#dl-cdn.alpinelinux.org#mirrors.aliyun.com#g' /etc/apk/repositories \
    && apk add --no-cache ffmpeg

WORKDIR /app

# 从构建阶段复制 JAR
COPY --from=builder /app/target/*.jar app.jar

# 创建上传目录
RUN mkdir -p /app/uploads

EXPOSE 8080

# 使用 prod 环境配置, 通过环境变量注入敏感信息
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
