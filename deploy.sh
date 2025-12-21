#!/usr/bin/env bash

set -e

COMPOSE_FILE="docker-compose.yml"
GIT_BRANCH="main"   # 如果你用 master，这里改成 master

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
  git pull --rebase origin $GIT_BRANCH
}

build_and_up() {
  SERVICE=$1
  echo ">>> Building $SERVICE ..."
  docker-compose -f $COMPOSE_FILE build $SERVICE

  echo ">>> Starting $SERVICE (without touching mysql)..."
  docker-compose -f $COMPOSE_FILE up -d --no-deps $SERVICE
}

restart_only() {
  SERVICE=$1
  echo ">>> Restarting $SERVICE ..."
  docker-compose -f $COMPOSE_FILE restart $SERVICE
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
