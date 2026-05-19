#!/usr/bin/env sh
set -eu

APP_DIR="${APP_DIR:-/opt/fresh-food-system}"
DOMAIN="${DOMAIN:-}"
HTTP_PORT="${HTTP_PORT:-80}"
ENV_FILE="${APP_DIR}/.env"
COMPOSE_FILE="${APP_DIR}/docker-compose.yml"

log() {
    printf '\033[1;32m[domain]\033[0m %s\n' "$*"
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

require_domain() {
    [ -n "$DOMAIN" ] || die "请这样运行：DOMAIN=你的域名 sh domain-fix.sh"
    DOMAIN="$(printf '%s' "$DOMAIN" | sed 's#^http://##; s#^https://##; s#/$##')"
}

check_files() {
    [ -f "$ENV_FILE" ] || die "未找到 $ENV_FILE，请先运行 server-deploy.sh 完成部署。"
    [ -f "$COMPOSE_FILE" ] || die "未找到 $COMPOSE_FILE，请先运行 server-deploy.sh 完成部署。"
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

update_domain_env() {
    log "更新 H5/二维码基础地址为：http://${DOMAIN}"
    set_env_value "APP_H5_BASE_URL" "http://${DOMAIN}"
}

restart_service() {
    log "重启容器服务..."
    as_root docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d
}

show_dns() {
    log "DNS 解析检查："
    if have getent; then
        getent hosts "$DOMAIN" || warn "getent 未解析到 $DOMAIN"
    elif have nslookup; then
        nslookup "$DOMAIN" || warn "nslookup 未解析到 $DOMAIN"
    else
        warn "系统没有 getent/nslookup，跳过 DNS 解析显示。"
    fi
}

check_local_host_header() {
    log "容器本机 Host 头检查：http://127.0.0.1/ Host: ${DOMAIN}"
    if curl -fsSI -H "Host: ${DOMAIN}" "http://127.0.0.1:${HTTP_PORT}/" >/tmp/fresh-food-domain-index.headers 2>/tmp/fresh-food-domain-index.err; then
        sed -n '1,8p' /tmp/fresh-food-domain-index.headers
    else
        warn "本机 Host 头访问失败："
        sed -n '1,20p' /tmp/fresh-food-domain-index.err >&2 || true
    fi

    log "容器本机 API 检查：http://127.0.0.1/api/trace/public-config Host: ${DOMAIN}"
    if curl -fsS -H "Host: ${DOMAIN}" "http://127.0.0.1:${HTTP_PORT}/api/trace/public-config" >/tmp/fresh-food-domain-api.json 2>/tmp/fresh-food-domain-api.err; then
        cat /tmp/fresh-food-domain-api.json
        printf '\n'
    else
        warn "本机 API 访问失败："
        sed -n '1,20p' /tmp/fresh-food-domain-api.err >&2 || true
    fi
}

check_public_urls() {
    log "公网 HTTP 检查：http://${DOMAIN}"
    if curl -fsSI --max-time 15 "http://${DOMAIN}/" >/tmp/fresh-food-public-http.headers 2>/tmp/fresh-food-public-http.err; then
        sed -n '1,8p' /tmp/fresh-food-public-http.headers
    else
        warn "公网 HTTP 访问失败："
        sed -n '1,20p' /tmp/fresh-food-public-http.err >&2 || true
    fi

    log "公网 HTTPS 检查：https://${DOMAIN}"
    if curl -fsSI --max-time 15 "https://${DOMAIN}/" >/tmp/fresh-food-public-https.headers 2>/tmp/fresh-food-public-https.err; then
        sed -n '1,8p' /tmp/fresh-food-public-https.headers
        warn "HTTPS 已有响应。如果页面仍白屏，请打开浏览器开发者工具查看 Console/Network 报错。"
    else
        warn "HTTPS 当前不可用，这是未配置 SSL 证书时的正常结果。浏览器请明确访问 http://${DOMAIN}/"
    fi
}

show_logs_hint() {
    log "如仍白屏，请贴下面两条命令输出："
    printf 'docker compose --env-file %s -f %s ps\n' "$ENV_FILE" "$COMPOSE_FILE"
    printf 'docker compose --env-file %s -f %s logs --tail=120 fresh-food\n' "$ENV_FILE" "$COMPOSE_FILE"
}

main() {
    require_domain
    check_files
    update_domain_env
    restart_service
    show_dns
    check_local_host_header
    check_public_urls
    show_logs_hint
}

main "$@"
