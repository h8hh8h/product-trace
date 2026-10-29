# Uni-app H5 打包与宝塔部署指引

> 适用项目：`product-trace/frontend-uniapp`（Uni-app Vue3）
> 目标：打包 H5 静态文件 → 上传宝塔 → 通过 `h5.yourdomain.com` 访问，并支持 `/p/123` 扫码短链。

---

## 1. 发布前检查

| 检查项 | 文件 | 生产环境应设为 |
| --- | --- | --- |
| 接口地址 | `frontend-uniapp/common/config.js` | `DEFAULT_BASE_URL = 'https://api.yourdomain.com/api'` |
| H5 路由 | `frontend-uniapp/manifest.json` | `h5.router.mode = history`，`base = /` |
| H5 标题 | `frontend-uniapp/manifest.json` | `h5.title = 产品溯源` |
| 媒体地址 | `application-prod.yml` | `url-prefix: https://api.yourdomain.com/uploads` |
| 微信分享 | `application-prod.yml` / 环境变量 | `wx.mp.app-id`、`wx.mp.app-secret` |
| 跨域来源 | `deploy/nginx/bt_nginx_api.conf` | CORS `map` 中加入 `https://h5.yourdomain.com` |
| 公众号域名 | 微信公众平台 | 「JS 接口安全域名」填写 `h5.yourdomain.com` |

> 若 H5 与 API 同域（`h5.conf` 已把 `/api/` 反代到 8080），`BASE_URL` 也可写 `https://h5.yourdomain.com/api`，此时不会触发跨域。

---

## 2. HBuilderX 打包 H5

1. 打开 HBuilderX，导入项目目录 `d:\Trae\product-trace\frontend-uniapp`。
2. 顶部菜单选择 **发行 → 网站-PC Web 或手机 H5**。
3. 弹窗中填写：
   - 网站标题：`产品溯源`
   - 网站域名：`https://h5.yourdomain.com`
   - 运行的基础路径：`/`（history 模式必须为根路径）
4. 点击 **发行**，等待控制台出现「发行成功」。
5. 产物目录（HBuilderX 默认）：

```
frontend-uniapp/unpackage/dist/build/h5/
├── index.html
├── assets/
└── static/
```

> 若使用 CLI（`npm run build:h5`），产物在 `dist/build/h5/`，内容与上面完全一致。

**不要用浏览器直接双击 `index.html` 测试**：history 路由必须通过 HTTP 服务访问。

---

## 3. 上传到宝塔

目标目录（与 `deploy/nginx/h5.conf` 的 `root` 一致）：

```
/www/wwwroot/product-trace/h5/dist/
```

操作步骤：

1. 宝塔 → 文件 → 进入 `/www/wwwroot/product-trace/`，新建目录 `h5`，其下再建 `dist`。
2. 将 `unpackage/dist/build/h5/` 下的**所有内容**（`index.html`、`assets/`、`static/`）上传到 `dist/` 内。
   - 正确：`/www/wwwroot/product-trace/h5/dist/index.html`
   - 错误：`/www/wwwroot/product-trace/h5/dist/h5/index.html`（多套一层会 404）
3. 设置权限：

```bash
chown -R www:www /www/wwwroot/product-trace/h5
chmod -R 755 /www/wwwroot/product-trace/h5
```

4. 宝塔 → 网站 → 新建站点 `h5.yourdomain.com`，根目录指向 `/www/wwwroot/product-trace/h5/dist`。
5. 申请 SSL，并把 `deploy/nginx/h5.conf` 的 server 内容合并到该站点配置（或直接替换站点配置文件）。
6. 重载 Nginx：

```bash
nginx -t && nginx -s reload
```

关键配置（`h5.conf` 已包含）：

```nginx
location / {
    try_files $uri $uri/ /index.html;   # 支持 /p/123 刷新与 history 路由
    add_header Cache-Control "no-cache";
}
location /assets/ {
    expires 30d;
    add_header Cache-Control "public, immutable";
}
```

---

## 4. 媒体目录迁移（从旧目录升级时）

`application-prod.yml` 的媒体根目录已调整为 `/www/wwwroot/media/uploads`，
对外 URL 为 `/uploads/images/...`、`/uploads/videos/...`。若之前已有素材：

```bash
mkdir -p /www/wwwroot/media/uploads
mv /www/wwwroot/media/images /www/wwwroot/media/uploads/ 2>/dev/null
mv /www/wwwroot/media/videos /www/wwwroot/media/uploads/ 2>/dev/null
chown -R www:www /www/wwwroot/media
```

历史图片补 WebP（可选，上传时已自动生成）：

```bash
bash /path/to/deploy/nginx/convert-webp.sh
```

---

## 5. 发布后验证清单

1. `https://h5.yourdomain.com/` 正常打开，无白屏。
2. `https://h5.yourdomain.com/p/1` 能进入产品详情（Nginx 回退 `index.html` + 前端解析 pathname）。
3. `https://h5.yourdomain.com/pages/product/detail?id=1&code=真实码值` 显示防伪提示。
4. 浏览器 Network：
   - `/api/product/1` 返回 200；
   - 图片响应头包含 `Vary: Accept`，`Accept: image/webp` 时 `Content-Type: image/webp`（已上传 WebP 的情况下）；
   - 视频请求返回 `206 Partial Content`，进度条可拖动。
5. 微信内打开页面，点击右上角「···」→ 分享，卡片标题与产品封面正确。
6. 刷新详情页不出现 404（history 路由回退生效）。

---

## 6. 更新发布与回滚

1. 重新发行 H5，得到新的 `unpackage/dist/build/h5/`。
2. 备份旧版本：

```bash
cd /www/wwwroot/product-trace/h5
mv dist dist_bak_$(date +%Y%m%d%H%M)
mkdir dist
```

3. 上传新产物到 `dist/`，`chown`/`chmod` 同第 3 节。
4. 静态文件覆盖**不需要**重载 Nginx；只有修改站点配置时才执行 `nginx -s reload`。
5. 回滚：删除新 `dist`，把 `dist_bak_*` 改回 `dist` 即可。

---

## 7. 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 刷新 `/p/123` 404 | 未配置 SPA 回退 | `try_files $uri $uri/ /index.html;` |
| 接口跨域失败 | CORS 未放行 H5 域名 | 在 `bt_nginx_api.conf` 的 `map $http_origin` 中加入 `https://h5.yourdomain.com` |
| 图片 404 | 媒体目录/前缀不一致 | 确认文件在 `/www/wwwroot/media/uploads/images/`，后端 `url-prefix` 为 `https://api.yourdomain.com/uploads` |
| 视频无法拖动 | mp4 未 faststart / 缺少 mp4 模块 | 后端 FFmpeg 已加 `+faststart`；确认 Nginx `nginx -V` 含 `http_mp4_module` |
| 视频 403 | Referer 白名单未包含 H5 域名 | 修改 `valid_referers` 后 `nginx -s reload` |
| 微信分享无标题/图片 | 未配置公众号或安全域名 | 配置 `wx.mp` 与「JS 接口安全域名」；分享图必须为公网 https 图片 |
| 小程序真机接口失败 | 使用了 127.0.0.1 或未配置合法域名 | 改为局域网 IP 联调；正式环境在公众平台配置 request 合法域名 |
