#!/usr/bin/env bash

set -e

COMPOSE_FILE="docker-compose.yml"
GIT_BRANCH="testversion "   # 如果你用 master，这里改成 master

print_usage() {
  echo ""
  echo "Usage:"
  echo "  ./deploy.sh server        # git pull + build + up server"
  echo "  ./deploy.sh node          # git pull + build + up node"
  echo "  ./deploy.sh all           # git pull + build + up server + node"
  echo "  ./deploy.sh restart srv   # 只重启 server（不 pull / build）"
  echo "  ./deploy.sh restart node  # 只重启 node（不 pull / build）"
  echo ""
}

git_pull() {
  echo ">>> Pulling latest code from git ($GIT_BRANCH)..."
  git fetch origin
  git checkout $GIT_BRANCH
  
  # 检查是否有未暂存的变更，如果有则直接丢弃
  if ! git diff-index --quiet HEAD --; then
    echo ">>> Discarding local changes..."
    git reset --hard HEAD
  fi
  
  # 执行 rebase pull
  git pull --rebase origin $GIT_BRANCH
}

prepare_vosk_model() {
  # 如果 /home/vosk-model-cn-0.22.zip 存在，复制到项目根目录供 Docker 构建使用
  # 这样可以在构建时使用本地文件，避免每次都要下载
  if [ -f "/home/vosk-model-cn-0.22.zip" ]; then
    echo ">>> Copying vosk-model-cn-0.22.zip from /home to project root..."
    cp -f /home/vosk-model-cn-0.22.zip ./vosk-model-cn-0.22.zip
    echo ">>> File copied successfully"
  else
    echo ">>> /home/vosk-model-cn-0.22.zip not found, will download from network during build"
    # 如果源文件不存在，删除项目根目录中的旧文件（如果有）
    if [ -f "./vosk-model-cn-0.22.zip" ]; then
      echo ">>> Removing old vosk-model-cn-0.22.zip from project root"
      rm -f ./vosk-model-cn-0.22.zip
    fi
  fi
}

build_and_up() {
  SERVICE=$1
  
  # 如果是 server 服务，准备 Vosk 模型文件
  if [ "$SERVICE" = "server" ]; then
    prepare_vosk_model
  fi
  
  echo ">>> Building $SERVICE ..."
  docker compose -f $COMPOSE_FILE build $SERVICE

  # 先停止并删除可能存在的同名旧容器，避免端口冲突
  echo ">>> Stopping and removing old containers for $SERVICE (if any)..."
  docker compose -f $COMPOSE_FILE stop $SERVICE 2>/dev/null || true
  docker compose -f $COMPOSE_FILE rm -f $SERVICE 2>/dev/null || true

  echo ">>> Starting $SERVICE (without touching mysql)..."
  docker compose -f $COMPOSE_FILE up -d --no-deps $SERVICE
}

restart_only() {
  SERVICE=$1
  echo ">>> Restarting $SERVICE ..."
  docker compose -f $COMPOSE_FILE restart $SERVICE
}

if [ $# -lt 1 ]; then
  print_usage
  exit 1
fi

ACTION=$1
TARGET=$2

case "$ACTION" in
  server)
    git_pull
    build_and_up server
    ;;
  node)
    git_pull
    build_and_up node
    ;;
  all)
    git_pull
    build_and_up server
    build_and_up node
    ;;
  restart)
    if [ -z "$TARGET" ]; then
      echo "Missing restart target (server|node)"
      exit 1
    fi
    restart_only $TARGET
    ;;
  *)
    print_usage
    exit 1
    ;;
esac

echo ">>> Deploy finished successfully."
