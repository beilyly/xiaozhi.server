# Docker 部署指南

本文档提供了使用Docker容器化部署Xiaozhi ESP32 Server Java项目的详细步骤。

## 前提条件

- 安装 [Docker](https://docs.docker.com/get-docker/)
- 安装 [Docker Compose](https://docs.docker.com/compose/install/)
- 确保以下端口在您的服务器上可用：
  - 13306 (MySQL)
  - 8084 (前端Node服务)
  - 8091 (后端Java服务)

## 部署步骤

### 1. 获取项目代码

```bash
git clone https://github.com/joey-zhou/xiaozhi-esp32-server-java/
cd xiaozhi-esp32-server-java
```

### 2. 启动Docker容器

```bash
docker-compose up -d
```

这将启动三个服务:
- MySQL数据库 (端口13306)
- 前端Node服务 (端口8084)
- 后端Java服务 (端口8091)

首次启动可能需要一些时间，因为需要下载Docker镜像、构建应用程序并下载相关依赖。

### 3. 访问应用

- 前端界面: http://localhost:8084
- 后端API: http://localhost:8091
- WebSocket服务: ws://宿主机IP:8091/ws/xiaozhi/v1/

**注意**: 在ESP32设备连接时，需要使用宿主机的实际IP地址，而不是localhost。

### 4. 查看日志

```bash
# 查看所有容器日志
docker-compose logs

# 查看特定服务日志
docker-compose logs server
docker-compose logs node
docker-compose logs mysql

# 实时查看日志
docker-compose logs -f server
```

### 5. 停止服务

```bash
# 停止并保留容器
docker-compose stop

# 停止并移除容器
docker-compose down

# 停止并移除容器及卷（会删除所有数据）
docker-compose down -v
```

## 环境变量配置

可以通过设置环境变量来自定义部署:

- `VOSK_MODEL_SIZE`: 设置语音识别模型大小，默认为"small"，可选值有"standard"（大模型下载慢，识别效果好），"small"（小模型下载快，识别效果差）。

例如，使用大型语音模型启动:

```bash
VOSK_MODEL_SIZE=standard docker-compose up -d
```

## 持久化数据

Docker部署方案已配置以下持久化卷:

- `mysql_data`: MySQL数据库数据
- `maven_repo`: Maven仓库缓存
- `vosk_models`: Vosk语音识别模型缓存

这些卷确保即使容器被删除，数据也不会丢失。要查看持久化卷的信息：

```bash
docker volume ls | grep xiaozhi
```

## 端口映射

默认端口映射如下:

- MySQL: 13306 -> 3306 (容器内)
- 前端Node服务: 8084 -> 8084 (容器内)
- 后端Java服务: 8091 -> 8091 (容器内)

如需修改端口映射，请编辑`docker-compose.yml`文件中的`ports`部分。例如，将前端端口从8084改为80：

```yaml
node:
  ports:
    - "80:8084"
```

## 系统要求

根据不同的使用场景，推荐的系统配置如下：

- **最低配置**：
  - 2核CPU
  - 2GB RAM
  - 10GB 存储空间
  - 该配置不适用于本地语音识别，需添加第三方识别 API

- **推荐配置**：
  - 2核CPU
  - 4GB RAM
  - 20GB+ 存储空间
  - 该配置适用于本地语音识别，但可能需要选择较小的 Vosk 模型以减少内存占用。

- **使用大型语音模型**：
  - 4核CPU
  - 8GB RAM
  - 30GB+ 存储空间
  - 该配置适用于本地语音识别，可使用较大的 Vosk 模型

## 故障排除

### 1. 容器启动失败

检查容器状态:

```bash
docker-compose ps
```

查看失败容器的日志:

```bash
docker-compose logs <service_name>
```

### 2. 数据库连接问题

确保MySQL容器健康检查通过:

```bash
docker-compose ps mysql
```

如果状态不是"healthy"，检查MySQL日志:

```bash
docker-compose logs mysql
```

常见问题包括：
- 数据库初始化失败
- 权限问题
- 端口冲突

### 3. 重建容器

如果需要重新构建容器:

```bash
docker-compose build --no-cache
docker-compose up -d
```

### 4. WebSocket连接问题

如果ESP32设备无法连接到WebSocket服务，请检查：

1. 确保使用的是宿主机的实际IP地址，而不是localhost
2. 确保8091端口已在防火墙中开放
3. 检查服务器日志中是否有连接尝试记录

```bash
docker-compose logs server
```

## 更新应用

要更新到最新版本：

```bash
# 拉取最新代码
git pull

# 重新构建并启动容器
docker-compose build
docker-compose up -d
```