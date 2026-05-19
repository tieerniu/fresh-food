#!/usr/bin/env bash
set -euo pipefail

export MYSQL_DATABASE="${MYSQL_DATABASE:-fresh_food_db}"
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456}"
export MYSQL_USER="${MYSQL_USER:-fresh}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-fresh_password}"
export TZ="${TZ:-Asia/Shanghai}"
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://127.0.0.1:3306/${MYSQL_DATABASE}?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-${MYSQL_USER}}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-${MYSQL_PASSWORD}}"
export APP_H5_BASE_URL="${APP_H5_BASE_URL:-}"

mysql_pid=""
app_pid=""

stop_processes() {
    if [ -n "${app_pid}" ] && kill -0 "${app_pid}" 2>/dev/null; then
        kill "${app_pid}" 2>/dev/null || true
    fi

    nginx -s quit 2>/dev/null || true

    if [ -n "${mysql_pid}" ] && kill -0 "${mysql_pid}" 2>/dev/null; then
        mysqladmin shutdown -h127.0.0.1 -uroot -p"${MYSQL_ROOT_PASSWORD}" 2>/dev/null \
            || kill "${mysql_pid}" 2>/dev/null \
            || true
    fi
}

handle_shutdown() {
    stop_processes
    exit 0
}

trap stop_processes EXIT
trap handle_shutdown SIGINT SIGTERM

docker-entrypoint.sh mysqld &
mysql_pid="$!"

echo "Waiting for MySQL to accept connections..."
for i in $(seq 1 90); do
    if mysqladmin ping -h127.0.0.1 -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent; then
        break
    fi

    if ! kill -0 "${mysql_pid}" 2>/dev/null; then
        echo "MySQL exited while starting."
        wait "${mysql_pid}"
    fi

    if [ "${i}" -eq 90 ]; then
        echo "Timed out waiting for MySQL."
        exit 1
    fi

    sleep 2
done

nginx

java ${JAVA_OPTS:-} -jar /app/app.jar &
app_pid="$!"

while true; do
    if ! kill -0 "${mysql_pid}" 2>/dev/null; then
        echo "MySQL process stopped."
        wait "${mysql_pid}"
    fi

    if ! kill -0 "${app_pid}" 2>/dev/null; then
        echo "Spring Boot process stopped."
        wait "${app_pid}"
    fi

    sleep 2
done
