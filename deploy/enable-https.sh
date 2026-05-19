#!/usr/bin/env sh
set -eu

APP_DIR="${APP_DIR:-/opt/fresh-food-system}"
DOMAIN="${DOMAIN:-}"
ALIAS_DOMAIN="${ALIAS_DOMAIN:-}"
EMAIL="${EMAIL:-}"
ENV_FILE="${APP_DIR}/.env"
COMPOSE_FILE="${APP_DIR}/docker-compose.yml"
CADDYFILE="${APP_DIR}/Caddyfile"

log() {
    printf '\033[1;32m[https]\033[0m %s\n' "$*"
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

clean_domain() {
    printf '%s' "$1" | sed 's#^http://##; s#^https://##; s#/$##'
}

set_env_value() {
    key="$1"
    value="$2"
    if grep -q "^${key}=" "$ENV_FILE"; then
        as_root sed -i "s#^${key}=.*#${key}=${value}#" "$ENV_FILE"
    else
        printf '%s=%s\n' "$key" "$value" | as_root tee -a "$ENV_FILE" >/dev/null
    fi
}

require_ready() {
    [ -n "$DOMAIN" ] || die "请这样运行：DOMAIN=www.tieerniu.cn ALIAS_DOMAIN=tieerniu.cn sh enable-https.sh"
    DOMAIN="$(clean_domain "$DOMAIN")"
    if [ -n "$ALIAS_DOMAIN" ]; then
        ALIAS_DOMAIN="$(clean_domain "$ALIAS_DOMAIN")"
    fi
    [ -f "$ENV_FILE" ] || die "未找到 $ENV_FILE，请先运行 server-deploy.sh。"
    [ -f "$COMPOSE_FILE" ] || die "未找到 $COMPOSE_FILE，请先运行 server-deploy.sh。"
    as_root docker info >/dev/null 2>&1 || die "Docker 未运行。"
    as_root docker compose version >/dev/null 2>&1 || die "Docker Compose 插件不可用。"
}

open_firewall_ports() {
    if have ufw && as_root ufw status 2>/dev/null | grep -q "Status: active"; then
        log "开放 UFW 80/443 端口"
        as_root ufw allow 80/tcp >/dev/null 2>&1 || true
        as_root ufw allow 443/tcp >/dev/null 2>&1 || true
    fi

    if have firewall-cmd && as_root firewall-cmd --state >/dev/null 2>&1; then
        log "开放 firewalld 80/443 端口"
        as_root firewall-cmd --add-service=http --permanent >/dev/null 2>&1 || true
        as_root firewall-cmd --add-service=https --permanent >/dev/null 2>&1 || true
        as_root firewall-cmd --reload >/dev/null 2>&1 || true
    fi
}

write_caddyfile() {
    log "写入 Caddyfile：$CADDYFILE"
    tmp_file="$(mktemp)"

    {
        if [ -n "$EMAIL" ]; then
            printf '{\n    email %s\n}\n\n' "$EMAIL"
        fi

        printf '%s {\n' "$DOMAIN"
        printf '    encode gzip\n'
        printf '    reverse_proxy fresh-food:80\n'
        printf '}\n'

        if [ -n "$ALIAS_DOMAIN" ] && [ "$ALIAS_DOMAIN" != "$DOMAIN" ]; then
            printf '\n%s {\n' "$ALIAS_DOMAIN"
            printf '    redir https://%s{uri} permanent\n' "$DOMAIN"
            printf '}\n'
        fi
    } > "$tmp_file"

    as_root mv "$tmp_file" "$CADDYFILE"
    as_root chmod 644 "$CADDYFILE"
}

write_compose_file() {
    log "改写 compose：应用容器只在内部网络暴露 80，由 Caddy 对外提供 80/443"
    tmp_file="$(mktemp)"
    cat > "$tmp_file" <<'EOF'
name: fresh-food-system

services:
  fresh-food:
    image: ${IMAGE}
    container_name: ${CONTAINER_NAME}
    restart: unless-stopped
    expose:
      - "80"
    environment:
      TZ: ${TZ}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      JAVA_OPTS: ${JAVA_OPTS}
      APP_H5_BASE_URL: ${APP_H5_BASE_URL}
    volumes:
      - fresh-food-mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://127.0.0.1/api/trace/public-config >/dev/null || exit 1"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 90s

  caddy:
    image: caddy:2-alpine
    container_name: fresh-food-caddy
    restart: unless-stopped
    depends_on:
      fresh-food:
        condition: service_healthy
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - fresh-food-caddy-data:/data
      - fresh-food-caddy-config:/config

volumes:
  fresh-food-mysql-data:
  fresh-food-caddy-data:
  fresh-food-caddy-config:
EOF
    as_root mv "$tmp_file" "$COMPOSE_FILE"
    as_root chmod 644 "$COMPOSE_FILE"
}

deploy_https() {
    set_env_value "APP_H5_BASE_URL" "https://${DOMAIN}"

    log "拉取 Caddy 镜像"
    as_root docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" pull caddy

    log "先重建应用容器以释放宿主机 80 端口"
    as_root docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --remove-orphans fresh-food

    log "启动 Caddy 并自动申请 HTTPS 证书"
    as_root docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d
}

wait_for_https() {
    log "等待 HTTPS 生效：https://${DOMAIN}/"
    for i in $(seq 1 18); do
        if curl -fsSI --max-time 10 "https://${DOMAIN}/" >/tmp/fresh-food-https.headers 2>/tmp/fresh-food-https.err; then
            sed -n '1,8p' /tmp/fresh-food-https.headers
            log "HTTPS 已可访问。"
            return
        fi
        sleep 10
    done

    warn "HTTPS 暂未访问成功。请确认云服务器安全组已放行 80 和 443。"
    warn "查看 Caddy 日志：docker compose --env-file ${ENV_FILE} -f ${COMPOSE_FILE} logs --tail=120 caddy"
}

main() {
    require_ready
    open_firewall_ports
    write_caddyfile
    write_compose_file
    deploy_https
    wait_for_https
    log "完成。主域名：https://${DOMAIN}/"
}

main "$@"
