# syntax=docker/dockerfile:1.7

FROM node:22-bookworm-slim AS web-builder
WORKDIR /build/fresh-food-web
COPY fresh-food-web/package*.json ./
RUN npm ci
COPY fresh-food-web/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-17 AS api-builder
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM mysql:8.0-debian

ENV TZ=Asia/Shanghai \
    MYSQL_DATABASE=fresh_food_db \
    MYSQL_ROOT_PASSWORD=123456 \
    MYSQL_USER=fresh \
    MYSQL_PASSWORD=fresh_password \
    JAVA_OPTS=""

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        nginx \
        openjdk-17-jre-headless \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app /usr/share/nginx/html /docker-entrypoint-initdb.d \
    && rm -f /etc/nginx/sites-enabled/default

COPY --from=api-builder /build/target/traceability-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --from=web-builder /build/fresh-food-web/dist/ /usr/share/nginx/html/
COPY database/init.sql /docker-entrypoint-initdb.d/01-init.sql
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY deploy/entrypoint.sh /usr/local/bin/fresh-food-entrypoint.sh

RUN chmod +x /usr/local/bin/fresh-food-entrypoint.sh

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://127.0.0.1/api/trace/public-config >/dev/null || exit 1

ENTRYPOINT ["fresh-food-entrypoint.sh"]
