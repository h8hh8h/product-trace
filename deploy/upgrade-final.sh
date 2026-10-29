#!/usr/bin/env bash
# ============================================================
# upgrade-final.sh —— 产品溯源系统「全量整合版」在已有服务器上的升级脚本
#
# 适用场景：服务器已部署旧版本（数据库已有数据），本次升级内容：
#   - 媒体资产库（上传落库 + /admin/media/list + {货号}_{时间戳} 语义化命名）
#   - /api/upload/** 后端动态读盘（存储路径改配置即时生效）
#   - 防盗链绕过（前端 no-referrer）/ 导入字段长度校验 / 视频转码防覆写
#   - H5 首页全量产品+推荐置顶+搜索、Banner 16:5、卡片 1:1
#
# 用法（服务器上执行）：
#   1) 上传 release-final 目录到 /root/product-trace-final/
#   2) sed -i 's/\r$//' upgrade-final.sh
#   3) bash -n upgrade-final.sh        # 语法自检
#   4) chmod +x upgrade-final.sh
#   5) ./upgrade-final.sh              # 升级（会提示输入 MySQL 密码）
#      ./upgrade-final.sh rollback     # 回滚（jar + 两个前端，数据库补丁幂等无需回滚）
#
# 环境变量可覆盖默认值，例：
#   APP_ROOT=/www/wwwroot/product-trace DB_PASS='xxx' ./upgrade-final.sh
# ============================================================
set -uo pipefail

# ================= 可配置区 =================
APP_ROOT="${APP_ROOT:-/www/wwwroot/product-trace}"
API_DIR="$APP_ROOT/api"
ADMIN_DIR="$APP_ROOT/admin"
H5_DIR="$APP_ROOT/h5"
LOG_DIR="$APP_ROOT/logs"
PKG_DIR="${PKG_DIR:-/root/product-trace-final}"

DB_NAME="${DB_NAME:-product_trace}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-}"

JAR_NAME="product-trace.jar"
ADMIN_ZIP="admin-dist.zip"
H5_ZIP="h5-dist.zip"
STATE_FILE="$APP_ROOT/.upgrade-final.state"

HEALTH_URL="http://127.0.0.1:8080/api/health"
REDIS_CLI="${REDIS_CLI:-redis-cli --no-auth-warning}"

# ================= 工具函数 =================
log()  { echo "[$(date '+%F %T')] $*"; }
ok()   { log "  [OK] $*"; }
warn() { log "  [WARN] $*"; }
fail() { log "  [FAIL] $*"; exit 1; }

mysql_exec() {
  if [ -n "$DB_PASS" ]; then
    mysql -u"$DB_USER" -p"$DB_PASS" --default-character-set=utf8mb4 "$DB_NAME" -e "$1" 2>/dev/null
  else
    mysql -u"$DB_USER" -p --default-character-set=utf8mb4 "$DB_NAME" -e "$1"
  fi
}

mysql_file() {
  if [ -n "$DB_PASS" ]; then
    mysql -u"$DB_USER" -p"$DB_PASS" --default-character-set=utf8mb4 "$DB_NAME" < "$1"
  else
    mysql -u"$DB_USER" -p --default-character-set=utf8mb4 "$DB_NAME" < "$1"
  fi
}

stop_app() {
  local pids
  pids=$(pgrep -f "$JAR_NAME" || true)
  if [ -z "$pids" ]; then ok "后端未运行"; return 0; fi
  kill $pids 2>/dev/null || true
  for i in $(seq 1 10); do
    pids=$(pgrep -f "$JAR_NAME" || true)
    [ -z "$pids" ] && { ok "后端已停止"; return 0; }
    sleep 1
  done
  warn "优雅停止超时，强制 kill -9"
  kill -9 $pids 2>/dev/null || true
  sleep 1
  pgrep -f "$JAR_NAME" >/dev/null && fail "进程未能终止" || ok "进程已强制终止"
}

start_app() {
  mkdir -p "$LOG_DIR"
  cd "$API_DIR"
  nohup java -jar -Xms512m -Xmx1024m -Dspring.profiles.active=prod \
    "$JAR_NAME" > "$LOG_DIR/app.out.log" 2> "$LOG_DIR/app.err.log" &
  log "后端已启动，PID=$!"
}

wait_health() {
  for i in $(seq 1 45); do
    sleep 2
    if curl -s --max-time 3 "$HEALTH_URL" | grep -q '"UP"'; then
      ok "健康检查通过（第 ${i} 次探测）"; return 0
    fi
  done
  return 1
}

flush_redis() {
  $REDIS_CLI ping >/dev/null 2>&1 || { warn "Redis 不可达，跳过缓存清理"; return 0; }
  $REDIS_CLI KEYS "trace:*" | xargs -r $REDIS_CLI DEL >/dev/null 2>&1 || true
  ok "Redis trace:* 缓存已清理"
}

# 检查 Nginx 站点配置中 /upload/ 是否已改为反代（旧 alias 写法会导致换存储路径后图片 404）
check_nginx() {
  log "检查 Nginx 站点 /upload/ 配置"
  local conf_dir="/www/server/panel/vhost/nginx"
  local bad=0
  for conf in "$conf_dir"/*.conf; do
    [ -f "$conf" ] || continue
    if grep -q "location /upload/" "$conf"; then
      if grep -A3 "location /upload/" "$conf" | grep -q "proxy_pass.*8080/api/upload/"; then
        ok "$(basename "$conf")：/upload/ 已是后端反代"
      elif grep -A3 "location /upload/" "$conf" | grep -q "alias"; then
        warn "$(basename "$conf")：/upload/ 仍是 alias 硬编码，换存储路径后图片会 404！"
        bad=1
      fi
    fi
  done
  if [ "$bad" = "1" ]; then
    cat <<'TIP'

  ==================== 需要手动修改 Nginx =================
  宝塔 → 网站 → 对应站点 → 配置文件，把三个站点里的：

      location /upload/ {
          alias /www/wwwroot/product-trace/upload/;
          ...
      }

  整段替换为：

      location /upload/ {
          proxy_pass         http://127.0.0.1:8080/api/upload/;
          proxy_http_version 1.1;
          proxy_set_header   Host              $host;
          proxy_set_header   X-Real-IP         $remote_addr;
          proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
          proxy_set_header   X-Forwarded-Proto $scheme;
          expires 30d;
          add_header Cache-Control "public";
          add_header Access-Control-Allow-Origin * always;
      }

  然后执行: nginx -t && nginx -s reload
  =========================================================
TIP
  fi
}

# ================= 回滚模式 =================
do_rollback() {
  log "===== 开始回滚 ====="
  [ -f "$STATE_FILE" ] || fail "找不到状态文件 $STATE_FILE（上次升级未用本脚本）"
  # shellcheck disable=SC1090
  . "$STATE_FILE"
  [ -n "${JAR_BAK:-}" ] || fail "状态文件缺少 JAR_BAK"
  [ -f "$JAR_BAK" ]     || fail "jar 备份不存在：$JAR_BAK"

  stop_app
  cp "$JAR_BAK" "$API_DIR/$JAR_NAME" && ok "jar 已回滚"

  if [ -n "${ADMIN_BAK:-}" ] && [ -f "$ADMIN_BAK" ]; then
    rm -rf "$ADMIN_DIR/dist"; mkdir -p "$ADMIN_DIR"
    tar -xzf "$ADMIN_BAK" -C "$ADMIN_DIR" && ok "后台 dist 已回滚"
    chown -R www:www "$ADMIN_DIR/dist" 2>/dev/null || true
  fi
  if [ -n "${H5_BAK:-}" ] && [ -f "$H5_BAK" ]; then
    rm -rf "$H5_DIR/dist"; mkdir -p "$H5_DIR"
    tar -xzf "$H5_BAK" -C "$H5_DIR" && ok "H5 dist 已回滚"
    chown -R www:www "$H5_DIR/dist" 2>/dev/null || true
  fi

  flush_redis
  start_app
  wait_health || fail "回滚后健康检查未通过，查看 $LOG_DIR/app.err.log"
  log "===== 回滚完成（media_file 表为新增表，对旧版本无影响，无需回滚数据库）====="
}

# ================= 升级模式 =================
do_upgrade() {
  log "===== 升级开始 ====="

  # ---------- 1. 前置检查 ----------
  log "[1/8] 前置检查"
  [ -f "$PKG_DIR/$JAR_NAME" ]  || fail "缺少 $PKG_DIR/$JAR_NAME"
  [ -f "$PKG_DIR/$ADMIN_ZIP" ] || fail "缺少 $PKG_DIR/$ADMIN_ZIP"
  [ -f "$PKG_DIR/$H5_ZIP" ]    || fail "缺少 $PKG_DIR/$H5_ZIP"
  ok "升级包文件齐全"
  java -version 2>&1 | head -1
  command -v mysql >/dev/null || fail "mysql 客户端不存在"
  command -v unzip >/dev/null || fail "unzip 不存在"
  command -v curl  >/dev/null || fail "curl 不存在"
  local free_mb
  free_mb=$(df -m "$APP_ROOT" | awk 'NR==2{print $4}')
  [ "${free_mb:-0}" -ge 512 ] || fail "磁盘空间不足 512MB（当前 ${free_mb}MB）"
  ok "磁盘空间 ${free_mb}MB"

  [ -z "$DB_PASS" ] && read -r -s -p "MySQL $DB_USER 密码: " DB_PASS && echo
  echo "SELECT 1" | { if [ -n "$DB_PASS" ]; then mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" >/dev/null 2>&1; else mysql -u"$DB_USER" -p "$DB_NAME" >/dev/null; fi; } \
    && ok "数据库连接正常" || fail "数据库连接失败"

  # ---------- 2. 备份 ----------
  log "[2/8] 备份"
  local ts db_bak jar_bak admin_bak h5_bak
  ts=$(date +%F_%H%M%S)
  db_bak="/root/product_trace_backup_${ts}.sql"
  if [ -n "$DB_PASS" ]; then
    mysqldump -u"$DB_USER" -p"$DB_PASS" --default-character-set=utf8mb4 --single-transaction "$DB_NAME" > "$db_bak" 2>/dev/null
  else
    mysqldump -u"$DB_USER" -p --default-character-set=utf8mb4 --single-transaction "$DB_NAME" > "$db_bak"
  fi
  [ -s "$db_bak" ] || fail "数据库备份失败"
  ok "数据库备份：$db_bak（$(du -h "$db_bak" | cut -f1)）"

  jar_bak="$API_DIR/${JAR_NAME}.bak-${ts}"
  [ -f "$API_DIR/$JAR_NAME" ] && cp "$API_DIR/$JAR_NAME" "$jar_bak" && ok "jar 备份：$jar_bak"

  admin_bak="/root/admin-dist-backup-${ts}.tgz"
  if [ -d "$ADMIN_DIR/dist" ]; then
    tar -czf "$admin_bak" -C "$ADMIN_DIR" dist && ok "后台前端备份：$admin_bak"
  else warn "旧后台 dist 不存在，跳过"; admin_bak=""; fi

  h5_bak="/root/h5-dist-backup-${ts}.tgz"
  if [ -d "$H5_DIR/dist" ]; then
    tar -czf "$h5_bak" -C "$H5_DIR" dist && ok "H5 前端备份：$h5_bak"
  else warn "旧 H5 dist 不存在，跳过"; h5_bak=""; fi

  cat > "$STATE_FILE" <<EOF
JAR_BAK="$jar_bak"
ADMIN_BAK="$admin_bak"
H5_BAK="$h5_bak"
DB_BAK="$db_bak"
EOF
  ok "回滚状态已写入 $STATE_FILE"

  # ---------- 3. 数据库幂等补丁（仅补新增表，不动已有数据） ----------
  log "[3/8] 数据库幂等补丁"
  local patch_sql="$PKG_DIR/.upgrade-patch.sql"
  cat > "$patch_sql" <<'SQL'
-- 媒体资产库表（与 init-all.sql 中定义完全一致）
CREATE TABLE IF NOT EXISTS `media_file` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `file_name`    VARCHAR(256) NOT NULL COMMENT '原始文件名',
    `storage_key`  VARCHAR(512) NOT NULL COMMENT '存储 key（相对路径/对象名）',
    `file_url`     VARCHAR(512) NOT NULL COMMENT '对外访问 URL',
    `media_type`   VARCHAR(16)  NOT NULL COMMENT '媒体类型：image/video/other',
    `file_size`    BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    `mime_type`    VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型',
    `storage_type` VARCHAR(16)  NOT NULL COMMENT '存储类型：local/oss/vod',
    `width`        INT          DEFAULT NULL COMMENT '图片宽度',
    `height`       INT          DEFAULT NULL COMMENT '图片高度',
    `duration`     INT          DEFAULT NULL COMMENT '视频时长（秒）',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-删除',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_storage_key` (`storage_key`),
    KEY `idx_media_type` (`media_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='媒体文件';

-- 存储配置表兜底（旧版本可能缺失）
CREATE TABLE IF NOT EXISTS sys_storage_config (
  id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键(固定为1)',
  storage_type        VARCHAR(16)  NOT NULL DEFAULT 'local' COMMENT '存储类型：local/oss',
  local_root_path     VARCHAR(512) NOT NULL DEFAULT '/www/wwwroot/product-trace/upload' COMMENT '本地根目录',
  local_url_prefix    VARCHAR(255) NOT NULL DEFAULT '/upload' COMMENT '本地URL前缀',
  oss_access_key_id   VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'OSS AccessKeyId',
  oss_access_key_secret VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'OSS AccessKeySecret',
  oss_bucket_name     VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'OSS Bucket',
  oss_endpoint        VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'OSS Endpoint',
  oss_url_prefix      VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'OSS自定义域名',
  update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='存储配置表(单行)';

INSERT IGNORE INTO sys_storage_config (id, storage_type) VALUES (1, 'local');
SQL
  mysql_file "$patch_sql" || fail "数据库补丁执行失败"
  rm -f "$patch_sql"
  local cols
  cols=$(mysql_exec "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='media_file';" | tail -1)
  [ "$cols" = "13" ] && ok "media_file 表结构就绪（13 列）" || warn "media_file 列数=$cols（期望 13），请核对 SHOW COLUMNS FROM media_file"

  # ---------- 4. 停服 + 换 jar + 启动 ----------
  log "[4/8] 替换 jar 并重启"
  stop_app
  cp "$PKG_DIR/$JAR_NAME" "$API_DIR/$JAR_NAME" || fail "jar 替换失败"
  ok "jar 已替换（$(du -h "$API_DIR/$JAR_NAME" | cut -f1)）"
  mkdir -p "$APP_ROOT/upload"
  chown -R www:www "$APP_ROOT/upload" 2>/dev/null || true
  flush_redis
  start_app
  if ! wait_health; then
    fail "健康检查超时！查看日志：tail -100 $LOG_DIR/app.err.log
    回滚命令：$0 rollback"
  fi

  # ---------- 5. 部署前端 ----------
  deploy_dist() {  # $1=zip $2=目标目录
    local tmp="$PKG_DIR/.dist-extract"
    rm -rf "$tmp" && mkdir -p "$tmp"
    unzip -o -q "$PKG_DIR/$1" -d "$tmp" || fail "$1 解压失败"
    [ -f "$tmp/index.html" ] || fail "$1 解压后缺少 index.html"
    mkdir -p "$2/dist"
    rm -rf "${2:?}/dist"/*
    cp -r "$tmp/"* "$2/dist/"
    chown -R www:www "$2/dist" 2>/dev/null || true
    rm -rf "$tmp"
  }
  log "[5/8] 部署前端"
  deploy_dist "$ADMIN_ZIP" "$ADMIN_DIR" && ok "后台 dist 已更新"
  deploy_dist "$H5_ZIP" "$H5_DIR"       && ok "H5 dist 已更新"

  # ---------- 6. Nginx 配置检测 ----------
  log "[6/8] Nginx 检测"
  check_nginx

  # ---------- 7. 冒烟 ----------
  log "[7/8] 冒烟检查"
  curl -s --max-time 5 "$HEALTH_URL" | grep -q '"UP"' && ok "health UP" || warn "health 异常"
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "http://127.0.0.1:8080/api/admin/media/list")
  [ "$code" = "401" ] && ok "媒体库接口鉴权正常（401）" || warn "media/list 无 token 返回 $code（期望 401）"
  # /api/upload/** 应由后端接管：未带 Referer 访问不存在的文件应返回 JSON 404 或 200，绝不是 nginx 的 html 404
  local up_code
  up_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "http://127.0.0.1:8080/api/upload/__probe__.jpg")
  { [ "$up_code" = "404" ] || [ "$up_code" = "200" ]; } && ok "/api/upload/** 路由已生效（$up_code）" || warn "/api/upload/** 返回 $up_code，请检查 jar 版本"
  grep -q "sys_storage_config 全部 10 个字段可读" "$LOG_DIR/app.out.log" && ok "启动自检通过（含 sys_storage_config）" || warn "未见启动自检日志，请检查 $LOG_DIR/app.out.log"

  # ---------- 8. 完成 ----------
  log "[8/8] 升级完成"
  cat <<EOF

  ==================== 验收要点 ====================
  1. 后台「产品管理」上传图片，文件名应为 {货号}_{时间戳}.jpg
  2. 「从媒体库选择」弹窗能看到刚上传的文件
  3. 后台「存储配置」改本地路径保存后，新上传立即落到新目录（需先完成上方 Nginx 修改）
  4. 1688/阿里图片链接粘贴到网络图片输入框能正常预览
  5. H5 首页：Banner 轮播、搜索框、推荐产品置顶
  ==================================================
  数据库备份：$db_bak
  回滚命令：  $0 rollback
EOF
}

# ================= 入口 =================
case "${1:-}" in
  rollback) do_rollback ;;
  "")       do_upgrade ;;
  *) echo "用法: $0 [rollback]"; exit 1 ;;
esac
