# ===== Stage 1: Maven 编译 =====
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 先复制 pom.xml 单独下载依赖（利用 Docker 缓存）
COPY pom.xml .
COPY settings.xml .
RUN mvn dependency:go-offline -B -s settings.xml

# 复制源码并打包
COPY src ./src
RUN mvn package -DskipTests -B -s settings.xml

# ===== Stage 2: 运行环境 =====
FROM eclipse-temurin:17-jre-alpine

# 安装 ffmpeg（项目依赖）
RUN apk add --no-cache ffmpeg

WORKDIR /app

# 从构建阶段复制 JAR
COPY --from=builder /app/target/*.jar app.jar

# 创建上传目录
RUN mkdir -p /app/uploads

EXPOSE 8080

# 使用 prod 环境配置, 通过环境变量注入敏感信息
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
