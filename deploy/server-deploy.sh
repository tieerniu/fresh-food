#!/usr/bin/env sh
set -eu

APP_DIR="${APP_DIR:-/opt/fresh-food-system}"
IMAGE="${IMAGE:-ghcr.io/tieerniu/fresh-food:latest}"
CONTAINER_NAME="${CONTAINER_NAME:-fresh-food}"
HTTP_PORT="${HTTP_PORT:-80}"
SERVER_HOST="${SERVER_HOST:-35.215.179.253}"
TZ="${TZ:-Asia/Shanghai}"
MYSQL_DATABASE="${MYSQL_DATABASE:-fresh_food_db}"
MYSQL_USER="${MYSQL_USER:-fresh}"
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx512m}"
APP_H5_BASE_URL="${APP_H5_BASE_URL:-http://${SERVER_HOST}}"
OPEN_FIREWALL="${OPEN_FIREWALL:-1}"

ENV_FILE="${APP_DIR}/.env"
COMPOSE_FILE="${APP_DIR}/docker-compose.yml"

log() {
    printf '\033[1;32m[deploy]\033[0m %s\n' "$*"
}

warn() {
    printf '\033[1;33m[warn]\033[0m %s\n' "$*" >&2
}

die() {
    printf '\033[1;31m[error]\033[0m %s\n' "$*" >&2
    exit 1
}

have() {
    command -v "$1" >/dev/null 2>&1
}

as_root() {
    if [ "$(id -u)" -eq 0 ]; then
        "$@"
    elif have sudo; then
        sudo "$@"
    else
        die "请使用 root 用户运行，或先安装 sudo。"
    fi
}

pkg_manager() {
    if have apt-get; then
        printf 'apt\n'
    elif have dnf; then
        printf 'dnf\n'
    elif have yum; then
        printf 'yum\n'
    elif have apk; then
        printf 'apk\n'
    else
        printf 'unknown\n'
    fi
}

install_base_tools() {
    pm="$(pkg_manager)"
    case "$pm" in
        apt)
            as_root apt-get update
            as_root env DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates curl openssl
            ;;
        dnf|yum)
            as_root "$pm" install -y ca-certificates curl openssl
            ;;
        apk)
            as_root apk add --no-cache ca-certificates curl openssl
            ;;
        *)
            have curl || die "无法识别系统包管理器，请先手动安装 curl 后再运行。"
            ;;
    esac
}

install_docker() {
    if have docker; then
        log "Docker 已安装，跳过安装。"
    else
        log "安装 Docker Engine..."
        tmp_script="/tmp/get-docker.sh"
        curl -fsSL https://get.docker.com -o "$tmp_script"
        as_root sh "$tmp_script"
    fi

    if have systemctl; then
        as_root systemctl enable --now docker >/dev/null 2>&1 || true
    fi

    if ! as_root docker info >/dev/null 2>&1; then
        as_root service docker start >/dev/null 2>&1 || true
        sleep 3
    fi

    as_root docker info >/dev/null 2>&1 || die "Docker 服务未能启动，请检查系统服务状态。"
}

install_compose_plugin() {
    if as_root docker compose version >/dev/null 2>&1; then
        log "Docker Compose 插件已可用。"
        return
    fi

    log "安装 Docker Compose 插件..."
    pm="$(pkg_manager)"
    case "$pm" in
        apt)
            as_root apt-get update
            as_root env DEBIAN_FRONTEND=noninteractive apt-get install -y docker-compose-plugin
            ;;
        dnf|yum)
            as_root "$pm" install -y docker-compose-plugin
            ;;
        apk)
            as_root apk add --no-cache docker-cli-compose
            ;;
        *)
            die "无法自动安装 Docker Compose 插件，请手动安装后重试。"
            ;;
    esac

    as_root docker compose version >/dev/null 2>&1 || die "Docker Compose 插件安装后仍不可用。"
}

random_password() {
    if have openssl; then
        openssl rand -base64 36 | tr -dc 'A-Za-z0-9' | cut -c 1-32
    else
        date "+%s%N" | cksum | tr -dc '0-9' | cut -c 1-32
    fi
}

write_env_file() {
    if [ -f "$ENV_FILE" ] && [ "${RESET_ENV:-0}" != "1" ]; then
        log "保留已有环境文件：$ENV_FILE"
        return
    fi

    MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-$(random_password)}"
    MYSQL_PASSWORD="${MYSQL_PASSWORD:-$(random_password)}"

    log "写入环境文件：$ENV_FILE"
    tmp_env="$(mktemp)"
    umask 077
    cat > "$tmp_env" <<EOF
IMAGE=${IMAGE}
CONTAINER_NAME=${CONTAINER_NAME}
HTTP_PORT=${HTTP_PORT}
TZ=${TZ}
MYSQL_DATABASE=${MYSQL_DATABASE}
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
MYSQL_USER=${MYSQL_USER}
MYSQL_PASSWORD=${MYSQL_PASSWORD}
JAVA_OPTS=${JAVA_OPTS}
APP_H5_BASE_URL=${APP_H5_BASE_URL}
EOF
    as_root mv "$tmp_env" "$ENV_FILE"
    as_root chmod 600 "$ENV_FILE"
}

write_compose_file() {
    log "写入 Docker Compose 文件：$COMPOSE_FILE"
    tmp_compose="$(mktemp)"
    cat > "$tmp_compose" <<'EOF'
name: fresh-food-system

services:
  fresh-food:
    image: ${IMAGE}
    container_name: ${CONTAINER_NAME}
    restart: unless-stopped
    environment:
      TZ: ${TZ}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      JAVA_OPTS: ${JAVA_OPTS}
      APP_H5_BASE_URL: ${APP_H5_BASE_URL}
    ports:
      - "${HTTP_PORT}:80"
    volumes:
      - fresh-food-mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://127.0.0.1/api/trace/public-config >/dev/null || exit 1"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 90s

volumes:
  fresh-food-mysql-data:
EOF
    as_root mv "$tmp_compose" "$COMPOSE_FILE"
    as_root chmod 644 "$COMPOSE_FILE"
}

login_ghcr_if_needed() {
    if [ -n "${GHCR_USERNAME:-}" ] && [ -n "${GHCR_TOKEN:-}" ]; then
        log "使用 GHCR_USERNAME/GHCR_TOKEN 登录 GitHub Container Registry..."
        printf '%s' "$GHCR_TOKEN" | as_root docker login ghcr.io -u "$GHCR_USERNAME" --password-stdin
    else
        log "未提供 GHCR 登录信息，将按公开镜像直接拉取。"
    fi
}

open_firewall_port() {
    [ "$OPEN_FIREWALL" = "1" ] || return

    if have ufw && as_root ufw status 2>/dev/null | grep -q "Status: active"; then
        log "开放 UFW 端口 ${HTTP_PORT}/tcp"
        as_root ufw allow "${HTTP_PORT}/tcp" >/dev/null 2>&1 || warn "UFW 端口开放失败，请手动检查。"
    fi

    if have firewall-cmd && as_root firewall-cmd --state >/dev/null 2>&1; then
        log "开放 firewalld 端口 ${HTTP_PORT}/tcp"
        as_root firewall-cmd --add-port="${HTTP_PORT}/tcp" --permanent >/dev/null 2>&1 || warn "firewalld 端口开放失败，请手动检查。"
        as_root firewall-cmd --reload >/dev/null 2>&1 || true
    fi
}

deploy_service() {
    log "拉取镜像：$IMAGE"
    if ! as_root docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" pull; then
        die "镜像拉取失败。如果 GitHub Packages 是私有包，请用：GHCR_USERNAME=你的GitHub用户名 GHCR_TOKEN=你的PAT sh server-deploy.sh"
    fi

    log "启动服务..."
    as_root docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d

    log "当前容器状态："
    as_root docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps
}

main() {
    log "开始部署生鲜防伪溯源系统。"
    install_base_tools
    install_docker
    install_compose_plugin
    as_root mkdir -p "$APP_DIR"
    write_env_file
    write_compose_file
    login_ghcr_if_needed
    open_firewall_port
    deploy_service
    log "部署完成：打开 http://${SERVER_HOST}:${HTTP_PORT}"
    log "查看日志：docker compose --env-file ${ENV_FILE} -f ${COMPOSE_FILE} logs -f"
}

main "$@"
