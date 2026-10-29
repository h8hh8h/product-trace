#!/usr/bin/env bash
# ============================================================
# deploy-v1.3.0.sh  —— 产品溯源系统 v1.3.0 一键部署 / 回滚
#
# 用法（服务器上执行）：
#   1) 上传部署包到 /root/product-trace-v1.3.0/：
#        product-trace-v1.3.0.jar      （后端新 jar）
#        admin-dist-v1.3.0.zip         （前端新 dist 压缩包）
#        migration-v3.sql / v4 / v5 / v6 / migration-v6-fix.sql
#   2) sed -i 's/\r$//' deploy-v1.3.0.sh   # Windows 编辑过务必去回车
#   3) bash -n deploy-v1.3.0.sh            # 语法自检
#   4) chmod +x deploy-v1.3.0.sh
#   5) ./deploy-v1.3.0.sh                  # 部署（会提示输入 MySQL root 密码）
#      ./deploy-v1.3.0.sh rollback         # 回滚（jar + 前端，数据库可选）
#
# 也可用环境变量覆盖默认路径，例：
#   APP_ROOT=/www/wwwroot/product-trace PKG_DIR=/root/pkg DB_PASS='xxx' ./deploy-v1.3.0.sh
# ============================================================
set -uo pipefail

# ================= 可配置区 =================
APP_ROOT="${APP_ROOT:-/www/wwwroot/product-trace}"
API_DIR="$APP_ROOT/api"
ADMIN_DIR="$APP_ROOT/admin"
LOG_DIR="$APP_ROOT/logs"
PKG_DIR="${PKG_DIR:-/root/product-trace-v1.3.0}"

DB_NAME="${DB_NAME:-product_trace}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-}"

JAR_NAME="product-trace.jar"
NEW_JAR="product-trace-v1.3.0.jar"
DIST_ZIP="admin-dist-v1.3.0.zip"
STATE_FILE="$APP_ROOT/.deploy-v1.3.0.state"

HEALTH_URL="http://127.0.0.1:8080/api/health"

REDIS_CLI="${REDIS_CLI:-redis-cli --no-auth-warning}"

# 严格迁移（任何一个失败即中止）
MIGRATIONS_STRICT=(migration-v3.sql migration-v4.sql migration-v5.sql migration-v6.sql)
# 容错迁移（1060/1061 重复列/索引属预期，--force 继续）
MIGRATIONS_FORCE=(migration-v6-fix.sql)

# ================= 工具函数 =================
log()  { echo "[$(date '+%F %T')] $*"; }
ok()   { log "  [OK] $*"; }
warn() { log "  [WARN] $*"; }
fail() { log "  [FAIL] $*"; exit 1; }

run_mysql() {  # $1=sql文件  $2=mode(strict|force)
  local f="$1" mode="${2:-strict}" opts=""
  [ "$mode" = "force" ] && opts="--force"
  if [ -n "$DB_PASS" ]; then
    mysql -u"$DB_USER" -p"$DB_PASS" --default-character-set=utf8mb4 $opts "$DB_NAME" < "$f"
  else
    mysql -u"$DB_USER" -p --default-character-set=utf8mb4 $opts "$DB_NAME" < "$f"
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
  nohup java -jar -Xms512m -Xmx1024m -Dspring.profiles.active=prod \
    "$JAR_NAME" > "$LOG_DIR/app.out.log" 2> "$LOG_DIR/app.err.log" &
  log "后端已启动，PID=$!"
}

wait_health() {  # 最多等 90 秒
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

# ================= 回滚模式 =================
do_rollback() {
  log "===== 开始回滚 ====="
  [ -f "$STATE_FILE" ] || fail "找不到状态文件 $STATE_FILE（上次部署未成功或未用本脚本）"
  # shellcheck disable=SC1090
  . "$STATE_FILE"
  [ -n "${JAR_BAK:-}" ]  || fail "状态文件缺少 JAR_BAK"
  [ -f "$JAR_BAK" ]      || fail "jar 备份不存在：$JAR_BAK"

  stop_app
  cp "$JAR_BAK" "$API_DIR/$JAR_NAME" && ok "jar 已回滚"

  if [ -n "${DIST_BAK:-}" ] && [ -f "$DIST_BAK" ]; then
    rm -rf "$ADMIN_DIR/dist"
    mkdir -p "$ADMIN_DIR"
    tar -xzf "$DIST_BAK" -C "$ADMIN_DIR" && ok "前端 dist 已回滚"
    chown -R www:www "$ADMIN_DIR/dist" 2>/dev/null || true
  else
    warn "无前端备份，跳过 dist 回滚"
  fi

  echo
  read -r -p "是否同时回滚数据库到备份？将丢失部署后新增数据！[yes/N] " ans
  if [ "$ans" = "yes" ] && [ -n "${DB_BAK:-}" ] && [ -f "$DB_BAK" ]; then
    [ -z "$DB_PASS" ] && read -r -s -p "MySQL $DB_USER 密码: " DB_PASS && echo
    if [ -n "$DB_PASS" ]; then
      mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$DB_BAK"
    else
      mysql -u"$DB_USER" -p "$DB_NAME" < "$DB_BAK"
    fi
    ok "数据库已回滚：$DB_BAK"
  else
    warn "跳过数据库回滚（新表/新列对旧版本无影响，可保留）"
  fi

  flush_redis
  start_app
  wait_health || fail "回滚后健康检查未通过，请查看 $LOG_DIR/app.err.log"
  log "===== 回滚完成 ====="
}

# ================= 部署模式 =================
do_deploy() {
  log "===== v1.3.0 部署开始 ====="

  # ---------- 1. 前置检查 ----------
  log "[1/9] 前置检查"
  [ -f "$PKG_DIR/$NEW_JAR" ]  || fail "缺少 $PKG_DIR/$NEW_JAR"
  [ -f "$PKG_DIR/$DIST_ZIP" ] || fail "缺少 $PKG_DIR/$DIST_ZIP"
  for f in "${MIGRATIONS_STRICT[@]}" "${MIGRATIONS_FORCE[@]}"; do
    [ -f "$PKG_DIR/$f" ] || fail "缺少 $PKG_DIR/$f"
  done
  ok "部署包文件齐全"
  java -version 2>&1 | head -1
  command -v mysql    >/dev/null || fail "mysql 客户端不存在"
  command -v unzip    >/dev/null || fail "unzip 不存在，请先 yum/apt 安装"
  command -v curl     >/dev/null || fail "curl 不存在"
  local free_mb
  free_mb=$(df -m "$APP_ROOT" | awk 'NR==2{print $4}')
  [ "${free_mb:-0}" -ge 1024 ] || fail "磁盘空间不足 1GB（当前 ${free_mb}MB）"
  ok "磁盘空间 ${free_mb}MB"
  [ -f "$API_DIR/$JAR_NAME" ] || warn "旧 jar 不存在（首次部署？）"

  [ -z "$DB_PASS" ] && read -r -s -p "MySQL $DB_USER 密码: " DB_PASS && echo
  echo "SELECT 1" | { if [ -n "$DB_PASS" ]; then mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" >/dev/null 2>&1; else mysql -u"$DB_USER" -p "$DB_NAME" >/dev/null; fi; } \
    && ok "数据库连接正常" || fail "数据库连接失败"

  # ---------- 2. 备份 ----------
  log "[2/9] 备份"
  local ts db_bak jar_bak dist_bak
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

  dist_bak="/root/admin-dist-backup-${ts}.tgz"
  if [ -d "$ADMIN_DIR/dist" ]; then
    tar -czf "$dist_bak" -C "$ADMIN_DIR" dist && ok "前端备份：$dist_bak"
  else
    warn "旧 dist 不存在，跳过前端备份"; dist_bak=""
  fi

  cat > "$STATE_FILE" <<EOF
JAR_BAK="$jar_bak"
DIST_BAK="$dist_bak"
DB_BAK="$db_bak"
EOF
  ok "回滚状态已写入 $STATE_FILE"

  # ---------- 3. 停服 ----------
  log "[3/9] 停止后端"
  stop_app

  # ---------- 4. 数据库迁移 ----------
  log "[4/9] 数据库迁移"
  for f in "${MIGRATIONS_STRICT[@]}"; do
    log "  执行 $f"
    run_mysql "$PKG_DIR/$f" strict || fail "$f 执行失败（数据库未回滚，可修复后重跑；jar 尚未替换）"
    ok "$f 完成"
  done
  for f in "${MIGRATIONS_FORCE[@]}"; do
    log "  执行 $f（容错模式，1060/1061 可忽略）"
    run_mysql "$PKG_DIR/$f" force || warn "$f 有报错，请人工核对上方输出"
    ok "$f 完成"
  done

  # 结构校验
  local cols_role cols_admin
  cols_role=$(mysql_exec "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='sys_role';" 2>/dev/null | tail -1)
  cols_admin=$(mysql_exec "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME='sys_admin' AND COLUMN_NAME IN ('role_id','must_change_password','password_update_time');" 2>/dev/null | tail -1)
  [ "$cols_role" = "8" ]  && ok "sys_role 结构正确（8 列）" || warn "sys_role 列数=$cols_role（期望 8），请执行 SHOW COLUMNS FROM sys_role 核对"
  [ "$cols_admin" = "3" ] && ok "sys_admin 新字段齐全"       || warn "sys_admin 新字段数=$cols_admin（期望 3），请核对"

  # ---------- 5. 替换 jar + 启动 ----------
  log "[5/9] 替换 jar 并启动"
  cp "$PKG_DIR/$NEW_JAR" "$API_DIR/$JAR_NAME" || fail "jar 替换失败"
  ok "jar 已替换（$(du -h "$API_DIR/$JAR_NAME" | cut -f1)）"
  flush_redis
  start_app
  if ! wait_health; then
    fail "健康检查超时！查看日志：tail -100 $LOG_DIR/app.err.log
    回滚命令：$0 rollback"
  fi

  # ---------- 6. 部署前端 ----------
  log "[6/9] 部署前端 dist"
  local tmp_dist="$PKG_DIR/.dist-extract"
  rm -rf "$tmp_dist" && mkdir -p "$tmp_dist"
  unzip -o -q "$PKG_DIR/$DIST_ZIP" -d "$tmp_dist" || fail "前端包解压失败"
  [ -f "$tmp_dist/index.html" ] || fail "解压后缺少 index.html，压缩包结构不对"
  mkdir -p "$ADMIN_DIR/dist"
  rm -rf "${ADMIN_DIR:?}/dist"/*
  cp -r "$tmp_dist/"* "$ADMIN_DIR/dist/"
  chown -R www:www "$ADMIN_DIR/dist" 2>/dev/null || true
  rm -rf "$tmp_dist"
  ok "前端部署完成"

  # ---------- 7. 冒烟 ----------
  log "[7/9] 冒烟检查"
  curl -s --max-time 5 "$HEALTH_URL" | grep -q '"UP"' && ok "health UP" || warn "health 异常"
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "http://127.0.0.1:8080/api/admin/stats/overview")
  [ "$code" = "401" ] && ok "鉴权拦截正常（401）" || warn "stats 无 token 返回 $code（期望 401）"
  grep -q "IP 地理位置库加载成功" "$LOG_DIR/app.out.log" && ok "IP 地理库已加载" || warn "未见地理库加载日志"

  # 启动自检（SystemSchemaCheck）：直接定位 sys_role/sys_admin/operation_log 缺列或权限问题
  local self_log self_ok
  self_log=""
  for f in "$LOG_DIR/product-trace.log" "$API_DIR/logs/product-trace.log" "$LOG_DIR/app.out.log"; do
    if [ -f "$f" ] && grep -q "启动自检" "$f"; then self_log="$f"; break; fi
  done
  if [ -n "$self_log" ]; then
    self_ok=$(grep -c "全部.*字段可读，权限正常" "$self_log" || true)
    if [ "$self_ok" = "3" ]; then
      ok "启动自检通过（sys_role/sys_admin/operation_log 三张表结构与权限正常）"
    else
      warn "启动自检发现问题，请查看：grep '启动自检' $self_log"
      grep "启动自检" "$self_log" | grep -E "缺列|权限|失败" | head -10
    fi
  else
    warn "未找到启动自检日志（确认已部署含 SystemSchemaCheck 的最新 jar）"
  fi

  # ---------- 8. 可选：定时备份 ----------
  log "[8/9] 定时备份（可选）"
  if [ -f "$PKG_DIR/backup.sh" ] && ! crontab -l 2>/dev/null | grep -q "backup.sh"; then
    cp "$PKG_DIR/backup.sh" "$APP_ROOT/backup.sh" && chmod +x "$APP_ROOT/backup.sh"
    (crontab -l 2>/dev/null; echo "0 3 * * * $APP_ROOT/backup.sh >> $LOG_DIR/backup.log 2>&1") | crontab -
    ok "已添加每日 03:00 备份任务"
  else
    ok "跳过（backup.sh 不在包内或任务已存在）"
  fi

  # ---------- 9. 完成 ----------
  log "[9/9] 部署完成"
  cat <<EOF

============================================================
 v1.3.0 部署成功！接下来请手工完成：
 1. 浏览器强刷（Ctrl+Shift+R）打开后台，用 admin 登录
    —— 本次升级会强制改密（≥8 位且含字母+数字）
 2. 验证「系统管理 → 角色管理 / 账号管理」列表正常加载
 3. 验证「产品管理 → 批量导入」下载模板并导入一次
 4. 验证「二维码管理 → 勾选 → 打印标签」
 5. 如有异常：tail -100 $LOG_DIR/app.err.log
    数据库报错现在会返回具体原因（如 Unknown column）
 回滚：$0 rollback
 回滚材料：jar_bak=$jar_bak
           dist_bak=$dist_bak
           db_bak=$db_bak
============================================================
EOF
}

# ================= 入口 =================
case "${1:-deploy}" in
  deploy)   do_deploy ;;
  rollback) do_rollback ;;
  *) echo "用法: $0 [deploy|rollback]"; exit 1 ;;
esac
