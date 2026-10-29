#!/bin/bash
# ============================================================
# 每日自动备份脚本（数据库 + upload 目录）
# 建议 crontab：0 3 * * * /www/wwwroot/product-trace/deploy/backup.sh >> /var/log/product-trace-backup.log 2>&1
#
# 保留策略：30 天，超出自动删除
# ============================================================
set -uo pipefail

# ---------- 可按实际环境修改 ----------
DB_NAME="product_trace"
DB_USER="root"
DB_PASS="${DB_PASS:-}"          # 建议通过环境变量或 ~/.my.cnf 提供，不要写死在脚本里
APP_ROOT="/www/wwwroot/product-trace"
UPLOAD_DIR="${APP_ROOT}/upload"
BACKUP_DIR="/www/wwwroot/backup/product-trace"
KEEP_DAYS=30
# --------------------------------------

DATE=$(date +%F_%H%M)
mkdir -p "${BACKUP_DIR}/db" "${BACKUP_DIR}/upload"

log() { echo "[$(date '+%F %T')] $*"; }

# 1) 数据库备份
DB_FILE="${BACKUP_DIR}/db/${DB_NAME}_${DATE}.sql.gz"
log "开始备份数据库 -> ${DB_FILE}"
if [ -n "${DB_PASS}" ]; then
  MYSQL_PWD="${DB_PASS}" mysqldump -u"${DB_USER}" \
    --default-character-set=utf8mb4 \
    --single-transaction --quick --routines --events \
    "${DB_NAME}" | gzip > "${DB_FILE}"
else
  mysqldump -u"${DB_USER}" \
    --default-character-set=utf8mb4 \
    --single-transaction --quick --routines --events \
    "${DB_NAME}" | gzip > "${DB_FILE}"
fi

if [ ${PIPESTATUS[0]} -ne 0 ]; then
  log "❌ 数据库备份失败"
  rm -f "${DB_FILE}"
  exit 1
fi
log "✅ 数据库备份完成：$(du -h "${DB_FILE}" | cut -f1)"

# 2) upload 目录备份（只备份近 1 天变更过的文件，减少体积）
UP_FILE="${BACKUP_DIR}/upload/upload_${DATE}.tar.gz"
if [ -d "${UPLOAD_DIR}" ]; then
  log "开始备份 upload 目录 -> ${UP_FILE}"
  tar czf "${UP_FILE}" -C "$(dirname "${UPLOAD_DIR}")" "$(basename "${UPLOAD_DIR}")" 2>/dev/null
  if [ $? -eq 0 ]; then
    log "✅ upload 备份完成：$(du -h "${UP_FILE}" | cut -f1)"
  else
    log "⚠️ upload 备份失败（不影响数据库备份）"
    rm -f "${UP_FILE}"
  fi
else
  log "⚠️ 未找到 upload 目录：${UPLOAD_DIR}，跳过"
fi

# 3) 清理过期备份
DELETED=$(find "${BACKUP_DIR}" -type f -name "*.gz" -mtime +${KEEP_DAYS} -print -delete | wc -l)
log "清理过期备份 ${DELETED} 个（保留 ${KEEP_DAYS} 天）"

# 4) 汇总
log "当前备份占用：$(du -sh "${BACKUP_DIR}" | cut -f1)"
log "备份流程结束"
