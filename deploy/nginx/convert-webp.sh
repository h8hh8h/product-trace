#!/bin/bash
# ============================================================
# 图片批量转 WebP（宝塔定时任务，每天凌晨执行）
# 依赖：dnf install libwebp-tools  或  apt install webp
# 与 Nginx try_files $uri$webp_suffix 配合使用
# ============================================================

MEDIA_ROOT="/www/wwwroot/media"
QUALITY=80

# 遍历媒体目录下的 jpg/png，若“原文件名 + .webp”不存在则转换
# 命名规则必须与 Nginx 的 try_files $uri$webp_suffix 一致：
#   a.jpg -> a.jpg.webp
find "$MEDIA_ROOT" -type f \( -iname "*.jpg" -o -iname "*.jpeg" -o -iname "*.png" \) | while read -r img; do
    webp="${img}.webp"
    if [ ! -f "$webp" ]; then
        cwebp -q "$QUALITY" "$img" -o "$webp" >/dev/null 2>&1
        echo "converted: $img -> $webp"
    fi
done
