#!/usr/bin/env bash
# ============================================================
# deploy-final.sh —— 产品溯源系统「全量整合版」一键部署 / 回滚
#
# 适用场景：全新干净服务器首次部署（基于 init-all.sql，11 张表一次到位），
#           或已部署 v1.x 的服务器做全量覆盖升级。
#
# 部署包目录（从 release-final 上传到服务器）：
#   product-trace.jar              后端
#   admin-dist.zip                 管理后台 dist
#   h5-dist.zip                    用户端 H5 dist
#   mp-weixin-dist.zip             微信小程序产物（上传到微信开发者工具即可，无需解压到服务器）
#   init-all.sql                   全量建库脚本（含默认超管 admin / Admin@2026、sys_storage_config 行）
#   application-prod.yml           生产配置模板（外置覆盖 jar 内配置）
#   nginx-full-stack.conf          三站点 Nginx 配置
#   backup.sh                      定时备份脚本
#
# 用法：
#   1) scp -r release-final/* root@<server>:/root/product-trace-final/
#   2) sed -i 's/\r$//' deploy-final.sh       # Windows 编辑过务必去回车
#   3) bash -n deploy-final.sh && chmod +x deploy-final.sh
#   4) ./deploy-final.sh                       # 部署（交互输入 MySQL root 密码 + 数据库/Redis 密码）
#      ./deploy-final.sh rollback              # 回滚 jar + 前端，数据库可选
#
# 覆盖默认路径的环境变量：
#   APP_ROOT=/www/wwwroot/product-trace  PKG_DIR=/root/product-trace-final \
#   DB_NAME=product_trace  DB_USER=root  DB_PASS='xxx'  REDIS_PASS='xxx'  ./deploy-final.sh
# ============================================================
set -uo pipefail

# ================= 可配置区 =================
APP_ROOT="${APP_ROOT:-/www/wwwroot/product-trace}"
API_DIR="$APP_ROOT/api"
ADMIN_DIR="$APP_ROOT/admin"
H5_DIR="$APP_ROOT/h5"
LOG_DIR="$APP_ROOT/logs"
UPLOAD_DIR="$APP_ROOT/upload"
PKG_DIR="${PKG_DIR:-/root/product-trace-final}"

DB_NAME="${DB_NAME:-product_trace}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-}"
REDIS_PASS="${REDIS_PASS:-}"

JAR_NAME="product-trace.jar"
NEW_JAR="product-trace.jar"
ADMIN_ZIP="admin-dist.zip"
H5_ZIP="h5-dist.zip"
INIT_SQL="init-all.sql"
NGINX_CONF="nginx-full-stack.conf"
PROD_YML="application-prod.yml"
STATE_FILE="$APP_ROOT/.deploy-final.state"

HEALTH_URL="http://127.0.0.1:8080/api/health"
REDIS_CLI="${REDIS_CLI:-redis-cli --no-auth-warning}"

# ================= 工具函数 =================
log()  { echo "[$(date '+%F %T')] $*"; }
ok()   { log "  [OK] $*"; }
warn() { log "  [WARN] $*"; }
fail() { log "  [FAIL] $*"; exit 1; }

run_mysql_file() {  # $1=sql文件
  if [ -n "$DB_PASS" ]; then
    mysql -u"$DB_USER" -p"$DB_PASS" --default-character-set=utf8mb4 "$DB_NAME" < "$1"
  else
    mysql -u"$DB_USER" -p --default-character-set=utf8mb4 "$DB_NAME" < "$1"
  fi
}

mysql_exec() {
  if [ -n "$DB_PASS" ]; then
    mysql -u"$DB_USER" -p"$DB_PASS" --default-character-set=utf8mb4 "$DB_NAME" -e "$1"
  else
    mysql -u"$DB_USER" -p --default-character-set=utf8mb4 "$DB_NAME" -e "$1"
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
  # 外置 application-prod.yml 会自动覆盖 jar 内同名配置
  nohup java -jar -Xms512m -Xmx1024m -Dspring.profiles.active=prod \
    "$JAR_NAME" > "$LOG_DIR/app.out.log" 2> "$LOG_DIR/app.err.log" &
  log "后端已启动，PID=$!"
}

wait_health() {  # 最多等 120 秒
  for i in $(seq 1 60); do
    sleep 2
    if curl -s --max-time 3 "$HEALTH_URL" | grep -q '"UP"'; then
      ok "健康检查通过（第 ${i} 次探测）"; return 0
    fi
  done
  return 1
}

flush_redis() {
  if [ -n "$REDIS_PASS" ]; then
    $REDIS_CLI -a "$REDIS_PASS" ping >/dev/null 2>&1 || { warn "Redis 不可达，跳过缓存清理"; return 0; }
    $REDIS_CLI -a "$REDIS_PASS" KEYS "trace:*" 2>/dev/null | xargs -r $REDIS_CLI -a "$REDIS_PASS" DEL >/dev/null 2>&1 || true
  else
    $REDIS_CLI ping >/dev/null 2>&1 || { warn "Redis 不可达，跳过缓存清理"; return 0; }
    $REDIS_CLI KEYS "trace:*" 2>/dev/null | xargs -r $REDIS_CLI DEL >/dev/null 2>&1 || true
  fi
  ok "Redis trace:* 缓存已清理"
}

# ================= 回滚模式 =================
do_rollback() {
  log "===== 开始回滚 ====="
  [ -f "$STATE_FILE" ] || fail "找不到状态文件 $STATE_FILE"
  # shellcheck disable=SC1090
  . "$STATE_FILE"
  [ -n "${JAR_BAK:-}" ]  || fail "状态文件缺少 JAR_BAK"
  [ -f "$JAR_BAK" ]      || fail "jar 备份不存在：$JAR_BAK"

  stop_app
  cp "$JAR_BAK" "$API_DIR/$JAR_NAME" && ok "jar 已回滚"

  if [ -n "${ADMIN_BAK:-}" ] && [ -f "$ADMIN_BAK" ]; then
    rm -rf "$ADMIN_DIR/dist"
    mkdir -p "$ADMIN_DIR"
    tar -xzf "$ADMIN_BAK" -C "$ADMIN_DIR" && ok "后台 dist 已回滚"
    chown -R www:www "$ADMIN_DIR/dist" 2>/dev/null || true
  fi
  if [ -n "${H5_BAK:-}" ] && [ -f "$H5_BAK" ]; then
    rm -rf "$H5_DIR/dist"
    mkdir -p "$H5_DIR"
    tar -xzf "$H5_BAK" -C "$H5_DIR" && ok "H5 dist 已回滚"
    chown -R www:www "$H5_DIR/dist" 2>/dev/null || true
  fi

  echo
  read -r -p "是否同时回滚数据库到部署前备份？将丢失部署后新增数据！[yes/N] " ans
  if [ "$ans" = "yes" ] && [ -n "${DB_BAK:-}" ] && [ -f "$DB_BAK" ]; then
    [ -z "$DB_PASS" ] && read -r -s -p "MySQL $DB_USER 密码: " DB_PASS && echo
    if [ -n "$DB_PASS" ]; then
      mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$DB_BAK"
    else
      mysql -u"$DB_USER" -p "$DB_NAME" < "$DB_BAK"
    fi
    ok "数据库已回滚：$DB_BAK"
  else
    warn "跳过数据库回滚（新表对旧版本无影响，可保留）"
  fi

  flush_redis
  start_app
  wait_health || fail "回滚后健康检查未通过，请查看 $LOG_DIR/app.err.log"
  log "===== 回滚完成 ====="
}

# ================= 部署模式 =================
do_deploy() {
  log "===== 全量整合版部署开始 ====="

  # ---------- 1. 前置检查 ----------
  log "[1/9] 前置检查"
  [ -f "$PKG_DIR/$NEW_JAR" ]  || fail "缺少 $PKG_DIR/$NEW_JAR"
  [ -f "$PKG_DIR/$ADMIN_ZIP" ] || fail "缺少 $PKG_DIR/$ADMIN_ZIP"
  [ -f "$PKG_DIR/$H5_ZIP" ]    || fail "缺少 $PKG_DIR/$H5_ZIP"
  [ -f "$PKG_DIR/$INIT_SQL" ]  || fail "缺少 $PKG_DIR/$INIT_SQL"
  ok "部署包文件齐全（jar / admin / h5 / init-all.sql）"
  java -version 2>&1 | head -1
  command -v mysql    >/dev/null || fail "mysql 客户端不存在"
  command -v unzip    >/dev/null || fail "unzip 不存在，请先 yum/apt 安装"
  command -v curl     >/dev/null || fail "curl 不存在"
  local free_mb
  free_mb=$(df -m "$APP_ROOT" | awk 'NR==2{print $4}')
  [ "${free_mb:-0}" -ge 1024 ] || fail "磁盘空间不足 1GB（当前 ${free_mb}MB）"
  ok "磁盘空间 ${free_mb}MB"

  [ -z "$DB_PASS" ] && read -r -s -p "MySQL $DB_USER 密码: " DB_PASS && echo
  echo "SELECT 1" | { if [ -n "$DB_PASS" ]; then mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" >/dev/null 2>&1; else mysql -u"$DB_USER" -p "$DB_NAME" >/dev/null; fi; } \
    && ok "数据库连接正常" || fail "数据库连接失败（确认账号/密码/库名）"

  # ---------- 2. 备份 ----------
  log "[2/9] 备份"
  local ts db_bak jar_bak admin_bak h5_bak
  ts=$(date +%F_%H%M%S)
  db_bak="/root/product_trace_backup_${ts}.sql"
  if [ -n "$DB_PASS" ]; then
    mysqldump -u"$DB_USER" -p"$DB_PASS" --default-character-set=utf8mb4 --single-transaction "$DB_NAME" > "$db_bak"
  else
    mysqldump -u"$DB_USER" -p --default-character-set=utf8mb4 --single-transaction "$DB_NAME" > "$db_bak"
  fi
  [ -s "$db_bak" ] || fail "数据库备份失败"
  ok "数据库备份：$db_bak（$(du -h "$db_bak" | cut -f1)）"

  jar_bak="$API_DIR/${JAR_NAME}.bak-${ts}"
  [ -f "$API_DIR/$JAR_NAME" ] && cp "$API_DIR/$JAR_NAME" "$jar_bak" && ok "jar 备份：$jar_bak"

  admin_bak="/root/admin-dist-backup-${ts}.tgz"
  [ -d "$ADMIN_DIR/dist" ] && tar -czf "$admin_bak" -C "$ADMIN_DIR" dist && ok "后台 dist 备份：$admin_bak" || admin_bak=""
  h5_bak="/root/h5-dist-backup-${ts}.tgz"
  [ -d "$H5_DIR/dist" ] && tar -czf "$h5_bak" -C "$H5_DIR" dist && ok "H5 dist 备份：$h5_bak" || h5_bak=""

  cat > "$STATE_FILE" <<EOF
JAR_BAK="$jar_bak"
ADMIN_BAK="$admin_bak"
H5_BAK="$h5_bak"
DB_BAK="$db_bak"
EOF
  ok "回滚状态已写入 $STATE_FILE"

  # ---------- 3. 停服 ----------
  log "[3/9] 停止后端"
  stop_app

  # ---------- 4. 数据库初始化（全量建表 + 超管账号） ----------
  log "[4/9] 数据库初始化（init-all.sql）"
  # init-all.sql 内部会 DROP TABLE IF EXISTS 重建；如库有数据需确认
  local table_count
  table_count=$(mysql_exec "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME';" 2>/dev/null | tail -1)
  if [ "${table_count:-0}" -gt 0 ]; then
    echo
    warn "当前数据库已存在 ${table_count} 张表，init-all.sql 会 DROP 重建！"
    read -r -p "确认要覆盖重建数据库吗？会清空现有产品/码/扫码数据！[yes/N] " ans
    [ "$ans" = "yes" ] || fail "已取消。如需升级请改用增量迁移脚本。"
  fi
  run_mysql_file "$PKG_DIR/$INIT_SQL" || fail "init-all.sql 执行失败，请查看上方输出"
  ok "数据库初始化完成（11 张表 + admin/Admin@2026）"
  mysql_exec "SELECT id, storage_type, local_root_path FROM sys_storage_config;" | tail -1 | grep -q "^1" && ok "sys_storage_config 已写入默认本地配置"

  # ---------- 5. 替换 jar + 外置配置 + 启动 ----------
  log "[5/9] 替换 jar 并启动"
  mkdir -p "$API_DIR"
  cp "$PKG_DIR/$NEW_JAR" "$API_DIR/$JAR_NAME" || fail "jar 替换失败"
  ok "jar 已替换（$(du -h "$API_DIR/$JAR_NAME" | cut -f1)）"

  # 外置 application-prod.yml：用户手工修改后放入 api/ 目录即可覆盖 jar 内配置
  if [ -f "$PKG_DIR/$PROD_YML" ] && [ ! -f "$API_DIR/$PROD_YML" ]; then
    cp "$PKG_DIR/$PROD_YML" "$API_DIR/$PROD_YML"
    warn "已复制 application-prod.yml 模板到 $API_DIR/，请按手册第四节修改数据库/Redis/scan-base-url 后重启"
  fi

  # 媒体目录必须提前建好并授权给 www，否则 LocalStorageStrategy 写入失败
  mkdir -p "$UPLOAD_DIR"
  chown -R www:www "$UPLOAD_DIR" 2>/dev/null || true
  chmod -R 755 "$UPLOAD_DIR"

  flush_redis
  start_app
  if ! wait_health; then
    fail "健康检查超时！查看日志：tail -100 $LOG_DIR/app.err.log
    回滚命令：$0 rollback"
  fi

  # ---------- 6. 部署前端（后台 + H5） ----------
  log "[6/9] 部署前端 dist"
  deploy_dist "$PKG_DIR/$ADMIN_ZIP" "$ADMIN_DIR/dist" "后台"
  deploy_dist "$PKG_DIR/$H5_ZIP" "$H5_DIR/dist" "H5"

  # ---------- 7. Nginx 配置（复制参考文件，不自动 reload） ----------
  log "[7/9] Nginx 配置"
  if [ -f "$PKG_DIR/$NGINX_CONF" ]; then
    mkdir -p "$APP_ROOT/nginx"
    cp "$PKG_DIR/$NGINX_CONF" "$APP_ROOT/nginx/nginx-full-stack.conf"
    warn "Nginx 参考配置已复制到 $APP_ROOT/nginx/nginx-full-stack.conf"
    warn "请按手册第六节替换三个站点的域名/证书路径后，在宝塔「网站」中分别粘贴配置，然后 nginx -t && nginx -s reload"
  fi

  # ---------- 8. 冒烟检查 ----------
  log "[8/9] 冒烟检查"
  curl -s --max-time 5 "$HEALTH_URL" | grep -q '"UP"' && ok "health UP" || warn "health 异常"
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "http://127.0.0.1:8080/api/admin/storage-config")
  [ "$code" = "401" ] && ok "鉴权拦截正常（401）" || warn "storage-config 无 token 返回 $code（期望 401）"

  # 启动自检（SystemSchemaCheck）：11 张表应全部「字段可读，权限正常」
  local self_log self_ok
  self_log=""
  for f in "$LOG_DIR/product-trace.log" "$API_DIR/logs/product-trace.log" "$LOG_DIR/app.out.log"; do
    if [ -f "$f" ] && grep -q "启动自检" "$f"; then self_log="$f"; break; fi
  done
  if [ -n "$self_log" ]; then
    self_ok=$(grep -c "全部.*字段可读，权限正常" "$self_log" || true)
    if [ "$self_ok" -ge 4 ]; then
      ok "启动自检通过（核心表结构与权限正常，包含 sys_storage_config）"
    else
      warn "启动自检发现问题，请查看：grep '启动自检' $self_log"
      grep "启动自检" "$self_log" | grep -E "缺列|权限|失败" | head -10
    fi
  else
    warn "未找到启动自检日志"
  fi

  # ---------- 9. 可选：定时备份 ----------
  log "[9/9] 定时备份（可选）"
  if [ -f "$PKG_DIR/backup.sh" ] && ! crontab -l 2>/dev/null | grep -q "backup.sh"; then
    cp "$PKG_DIR/backup.sh" "$APP_ROOT/backup.sh" && chmod +x "$APP_ROOT/backup.sh"
    (crontab -l 2>/dev/null; echo "0 3 * * * $APP_ROOT/backup.sh >> $LOG_DIR/backup.log 2>&1") | crontab -
    ok "已添加每日 03:00 备份任务"
  else
    ok "跳过（backup.sh 不在包内或任务已存在）"
  fi

  log "===== 部署完成 ====="
  cat <<EOF

============================================================
 部署成功！接下来请手工完成：
 1. 修改 $API_DIR/application-prod.yml 中数据库密码、Redis 密码、scan-base-url
 2. 在宝塔「网站」按手册第六节配置三个站点（api/admin/h5）并 reload nginx
 3. 浏览器强刷打开 https://admin.你的域名.com
    —— 默认账号 admin / Admin@2026（首登不强制改密，验收后请立即改密）
 4. 验证「系统管理 → 存储配置」显示本地路径并保存成功
 5. 验证产品图片、视频、二维码显示与扫码链路正常
 6. 如有异常：tail -100 $LOG_DIR/app.err.log（数据库报错现在会返回具体原因）
 回滚：$0 rollback
 回滚材料：jar_bak=$jar_bak
           admin_bak=$admin_bak
           h5_bak=$h5_bak
           db_bak=$db_bak
============================================================
EOF
}

# 解压并部署一个前端 dist 目录
deploy_dist() {  # $1=zip文件  $2=目标dist目录  $3=名称
  local zip="$1" dest="$2" name="$3"
  local tmp="$PKG_DIR/.dist-extract"
  rm -rf "$tmp" && mkdir -p "$tmp"
  unzip -o -q "$zip" -d "$tmp" || fail "$name 包解压失败"
  [ -f "$tmp/index.html" ] || fail "$name 解压后缺少 index.html，压缩包结构不对"
  mkdir -p "$dest"
  rm -rf "${dest:?}"/*
  cp -r "$tmp/"* "$dest/"
  chown -R www:www "$dest" 2>/dev/null || true
  rm -rf "$tmp"
  ok "$name dist 部署完成"
}

# ================= 入口 =================
case "${1:-deploy}" in
  deploy)   do_deploy ;;
  rollback) do_rollback ;;
  *) echo "用法: $0 [deploy|rollback]"; exit 1 ;;
esac
