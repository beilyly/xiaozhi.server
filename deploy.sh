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
  # 获取语言参数，默认为 cn
  VOSK_MODEL_LANG=${VOSK_MODEL_LANG:-cn}
  
  if [ "$VOSK_MODEL_LANG" = "en" ]; then
    # 英文模型处理
    MODEL_FILE="vosk-model-en-us-0.22.zip"
    if [ -f "/home/${MODEL_FILE}" ]; then
      echo ">>> Copying ${MODEL_FILE} from /home to models directory..."
      cp -f /home/${MODEL_FILE} ./models/${MODEL_FILE}
      echo ">>> File copied successfully, Dockerfile will use local file"
    elif [ -f "./models/${MODEL_FILE}" ]; then
      echo ">>> Found ${MODEL_FILE} in models directory, Dockerfile will use it"
    else
      echo ">>> /home/${MODEL_FILE} and ./models/${MODEL_FILE} not found"
      # 如果源文件不存在，删除项目根目录和 models 目录中的旧文件（如果有）
      if [ -f "./${MODEL_FILE}" ]; then
        echo ">>> Removing old ${MODEL_FILE} from project root"
        rm -f ./${MODEL_FILE}
      fi
      if [ -f "./models/${MODEL_FILE}" ]; then
        echo ">>> Removing old ${MODEL_FILE} from models directory"
        rm -f ./models/${MODEL_FILE}
      fi
      echo ">>> Dockerfile will download model from network during build"
    fi
  else
    # 中文模型处理（默认）
    MODEL_FILE="vosk-model-cn-0.22.zip"
    if [ -f "/home/${MODEL_FILE}" ]; then
      echo ">>> Copying ${MODEL_FILE} from /home to models directory..."
      cp -f /home/${MODEL_FILE} ./models/${MODEL_FILE}
      echo ">>> File copied successfully, Dockerfile will use local file"
    elif [ -f "./models/${MODEL_FILE}" ]; then
      echo ">>> Found ${MODEL_FILE} in models directory, Dockerfile will use it"
    else
      echo ">>> /home/${MODEL_FILE} and ./models/${MODEL_FILE} not found"
      # 如果源文件不存在，删除项目根目录和 models 目录中的旧文件（如果有）
      if [ -f "./${MODEL_FILE}" ]; then
        echo ">>> Removing old ${MODEL_FILE} from project root"
        rm -f ./${MODEL_FILE}
      fi
      if [ -f "./models/${MODEL_FILE}" ]; then
        echo ">>> Removing old ${MODEL_FILE} from models directory"
        rm -f ./models/${MODEL_FILE}
      fi
      echo ">>> Dockerfile will download model from network during build"
    fi
  fi
}

cleanup_port() {
  PORT=$1
  echo ">>> Checking for containers using port $PORT..."
  
  # 查找所有容器，检查端口映射
  CONTAINER_IDS=$(docker ps -a --format "{{.ID}} {{.Ports}}" 2>/dev/null | grep -E ":$PORT->|0\.0\.0\.0:$PORT|:::$PORT" | awk '{print $1}' || true)
  
  if [ -n "$CONTAINER_IDS" ]; then
    echo ">>> Found containers using port $PORT, removing them..."
    for CID in $CONTAINER_IDS; do
      echo ">>> Stopping and removing container $CID..."
      docker stop $CID 2>/dev/null || true
      docker rm -f $CID 2>/dev/null || true
    done
  else
    echo ">>> No containers found using port $PORT"
  fi
}

build_and_up() {
  SERVICE=$1
  
  # 如果是 server 服务，准备 Vosk 模型文件
  if [ "$SERVICE" = "server" ]; then
    prepare_vosk_model
    # 清理占用 8091 端口的容器
    cleanup_port 8091
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
