#!/usr/bin/env sh
set -eu

APP_DIR="${APP_DIR:-/opt/fresh-food-system}"
SQL_FILE="${1:-${APP_DIR}/fresh_food_db.sql}"
ENV_FILE="${APP_DIR}/.env"
COMPOSE_FILE="${APP_DIR}/docker-compose.yml"
BACKUP_DIR="${APP_DIR}/db-backups"

log() {
    printf '\033[1;32m[db]\033[0m %s\n' "$*"
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

[ -f "$ENV_FILE" ] || die "未找到 ${ENV_FILE}，请先完成部署。"
[ -f "$COMPOSE_FILE" ] || die "未找到 ${COMPOSE_FILE}，请先完成部署。"
[ -f "$SQL_FILE" ] || die "未找到 SQL 文件：${SQL_FILE}。请先把 fresh_food_db.sql 上传到服务器。"

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

MYSQL_DATABASE="${MYSQL_DATABASE:-fresh_food_db}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456}"

as_root docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps fresh-food >/dev/null 2>&1 \
    || die "fresh-food 容器未运行。"

log "准备备份当前线上数据库：${MYSQL_DATABASE}"
as_root mkdir -p "$BACKUP_DIR"
backup_file="${BACKUP_DIR}/fresh_food_db_before_import_$(date +%Y%m%d_%H%M%S).sql"
as_root docker exec fresh-food mysqldump -uroot -p"${MYSQL_ROOT_PASSWORD}" \
    --databases "$MYSQL_DATABASE" --single-transaction --routines --triggers \
    > "$backup_file"
as_root chmod 600 "$backup_file"
log "已备份到：${backup_file}"

log "导入 SQL：${SQL_FILE}"
as_root docker exec -i fresh-food mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "$MYSQL_DATABASE" < "$SQL_FILE"

log "导入后快速检查数据量"
as_root docker exec fresh-food mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "$MYSQL_DATABASE" -e "
SELECT 'users' AS table_name, COUNT(*) AS count FROM users
UNION ALL SELECT 'product_batches', COUNT(*) FROM product_batches
UNION ALL SELECT 'qr_codes', COUNT(*) FROM qr_codes
UNION ALL SELECT 'traceability_records', COUNT(*) FROM traceability_records
UNION ALL SELECT 'batch_applications', COUNT(*) FROM batch_applications;
"

log "重启应用容器，让连接状态重新初始化"
as_root docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" restart fresh-food

log "完成。请用 admin/123456 登录，或使用 SQL 中已存在的企业账号。"
