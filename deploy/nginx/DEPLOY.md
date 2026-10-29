# Nginx 反向代理部署步骤（宝塔 Linux）

## 一、前置准备

服务器已安装：宝塔面板、Nginx、MySQL 5.7+/8.0、Redis、JDK 17。

部署包清单（来自 `product-trace-deliver-*.zip`）：

```
后端         target/product-trace.jar                     → 上传到 /www/wwwroot/product-trace/api/
管理后台     admin-web/dist/  全部内容                     → 上传到 /www/wwwroot/product-trace/admin/dist/
用户端 H5    frontend-uniapp/dist/build/h5/ 全部内容       → 上传到 /www/wwwroot/product-trace/h5/dist/
媒体目录     upload/                                       → 直接创建 /www/wwwroot/product-trace/upload/
数据库脚本   deploy/schema.sql                             → 先在本地或临时库执行过，已建库
Nginx 配置   deploy/nginx/full-stack.conf                  → 粘贴到宝塔站点或 include
```

## 二、创建目录结构

```bash
mkdir -p /www/wwwroot/product-trace/{api,admin/dist,h5/dist,upload/{images,videos,qrcode,csv}}
mkdir -p /www/wwwroot/product-trace/logs
```

## 三、上传文件

- **后端 jar**：上传 `target/product-trace.jar` → `/www/wwwroot/product-trace/api/`
- **管理后台**：用 FileZilla / 宝塔文件管理器，把 `admin-web/dist/` 下全部文件（含 assets 子目录）粘到 `/www/wwwroot/product-trace/admin/dist/`
- **H5**：把 `frontend-uniapp/dist/build/h5/` 全部内容粘到 `/www/wwwroot/product-trace/h5/dist/`
- **媒体 upload**：已有的二维码 / 图片可以拷贝到 `/www/wwwroot/product-trace/upload/` 对应子目录；新文件后端会自动写入

## 四、生产配置文件

后端需要切到 prod 配置。两种方式任选其一：

**方式 A：直接改 jar 内 application-prod.yml**（jar 未打包前改好；或临时解压改完重新打）

**方式 B：外置 yml（推荐，不用重新打包）**

在 `/www/wwwroot/product-trace/api/` 下新建 `application-prod.yml`（jar 同目录会自动扫描）：

```yaml
server:
  port: 8080
  servlet:
    context-path: /api

spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/product_trace?useSSL=false&characterEncoding=utf8&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    username: root
    password: 你的MySQL密码
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password: 你的Redis密码      # 没密码就删掉这一行
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 500MB

media:
  storage:
    type: local
    local:
      root-path: /www/wwwroot/product-trace/upload

trace:
  anti-fake:
    default-scan-limit: 3
  h5:
    scan-base-url: https://h5.yourdomain.com/scan
  admin:
    login-ip-check: false   # 宝塔内默认放行

wx:
  miniapp:
    app-id: ${WX_MINIAPP_APP_ID:}
    app-secret: ${WX_MINIAPP_APP_SECRET:}
    mock: false

logging:
  level:
    com.trace.product: info
  file:
    name: /www/wwwroot/product-trace/logs/product-trace.log
```

## 五、启动后端 Java 进程

**方式 1：宝塔 Java 项目管理器（推荐）**

1. 宝塔 → 软件商店 → 搜「Java 项目管理器」安装
2. 添加项目：
   - **项目名**：product-trace
   - **启动方式**：Jar 包
   - **Jar 路径**：`/www/wwwroot/product-trace/api/product-trace.jar`
   - **运行目录**：`/www/wwwroot/product-trace/api`
   - **启动参数**：`--spring.profiles.active=prod -Xms512m -Xmx1024m -Dfile.encoding=UTF-8`
   - **端口**：8080
3. 点击启动 → 查看日志 → 出现 `Started ProductTraceApplication in xx seconds` 即成功

**方式 2：systemd（无宝塔插件时）**

```bash
cat > /etc/systemd/system/product-trace.service <<EOF
[Unit]
Description=Product Trace Backend
After=network.target mysql.service redis.service

[Service]
Type=simple
WorkingDirectory=/www/wwwroot/product-trace/api
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -Dfile.encoding=UTF-8 \
  -jar /www/wwwroot/product-trace/api/product-trace.jar \
  --spring.profiles.active=prod
Restart=always
RestartSec=10
EnvironmentFile=/www/wwwroot/product-trace/api/product-trace.env

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable product-trace
systemctl start product-trace
systemctl status product-trace
```

env 文件 `/www/wwwroot/product-trace/api/product-trace.env`：

```
WX_MINIAPP_APP_ID=你的小程序AppID
WX_MINIAPP_APP_SECRET=你的小程序AppSecret
ANTI_FAKE_SCAN_LIMIT=3
```

## 六、数据库导入

```bash
# 本地 schema.sql 已含 admin 种子账号 admin/admin123
mysql -uroot -p product_trace < deploy/schema.sql
```

如果是后续迭代部署，schema.sql 已执行过 → 跳过；只需保证 `sys_admin` 表里有 admin 账号。

## 七、Nginx 站点配置

在宝塔 → 网站 → 添加站点，三个域名分别添加；或直接把 `full-stack.conf` 粘到「配置文件」里。

### 7.1 API 域名：api.yourdomain.com

宝塔添加站点 → 域名 `api.yourdomain.com` → 根目录任意（我们用不到）→ 申请 Let's Encrypt 证书。

然后打开站点「配置文件」窗口，把 `server { ... }` 整块替换为 **full-stack.conf 里第一个 server 块**（含 80 跳 443 和 443 反代）。

同理对 admin / h5 两个域名各建站点，替换对应 server 块。

### 7.2 关键注意事项

- **CORS 的 map 必须在 http 块**，不能写在单个 server 内。`trace_cors_origin` 这个 map 如果宝塔报 unknown 变量，需要把 map 段挪到主 nginx.conf 的 http {} 块里（宝塔「软件商店 → Nginx → 设置 → 配置修改」）
- **proxy_pass 不要带路径尾斜杠**：`proxy_pass http://127.0.0.1:8080`（没有 `/api`）。这样 `/api/admin/product` 原路径会完整转发给后端 `http://127.0.0.1:8080/api/admin/product`
- **media 的 alias vs root**：`location /upload/` 用 `alias /www/wwwroot/product-trace/upload/` 时，路径会正确拼接（`/upload/images/a.jpg` → `/www/wwwroot/product-trace/upload/images/a.jpg`）
- **SPA 回退**：admin 和 h5 两个站点的 `location /` 必须有 `try_files $uri $uri/ /index.html`，否则刷新子路由（如 `/product/1`）会 404
- **`listen 443 ssl http2`** 的 ssl_certificate 路径请改为宝塔实际生成的路径（通常在 `/www/server/panel/vhost/cert/站点域名/`）

### 7.3 配置测试

改完后先别点保存，在宝塔终端执行：

```bash
nginx -t          # 检查语法，必须看到 "syntax is ok" 和 "test is successful"
```

通过后再点宝塔「重载配置」按钮。

## 八、前端生产构建时的 baseURL 调整

admin-web 和 h5 在生产构建前，要把接口基地址改成线上域名，否则会请求 `localhost:8080`。

### 管理后台 admin-web

编辑 `admin-web/src/request.js`，把：

```js
const request = axios.create({
  baseURL: '/api',   // ← 改成
})
```

改为：

```js
const request = axios.create({
  baseURL: 'https://api.yourdomain.com/api',
})
```

然后 `npm run build` 重打包，把新的 `dist/` 覆盖到服务器 `/www/wwwroot/product-trace/admin/dist/`。

> 如果想保持 baseURL 为 `/api`（admin 域名下反代 `/api` 到后端），参考 full-stack.conf 里 admin 站点的 `/api/` location 已配好，可保留 `/api` 不变。**推荐用这种，前端不用改代码**。

### 用户端 H5

编辑 `frontend-uniapp/src/common/config.js`：

```js
const DEFAULT_BASE_URL = 'https://api.yourdomain.com/api'
```

然后 `npm run build:h5`，把新的 `dist/build/h5/` 覆盖到服务器。

## 九、上线验收清单

| # | 检查项 | 验证方法 |
|---|---|---|
| 1 | 后端健康检查 | `curl https://api.yourdomain.com/api/health` → `code:200` status:UP |
| 2 | 数据库表齐全 | `curl https://api.yourdomain.com/api/health/db` → 5/5 OK |
| 3 | 管理后台登录 | 浏览器打开 `https://admin.yourdomain.com` → admin/admin123 登录成功 |
| 4 | 产品列表 | 登录后产品管理页能加载到数据 |
| 5 | 上传图片 | 新建产品 → 上传封面 → 刷新仍可见 |
| 6 | 扫码 H5 | 访问 `https://h5.yourdomain.com/scan/CAAF86FAD092` → 显示产品详情 |
| 7 | 防伪正常 | 同一码扫到第 4 次 → 假货预警 UI |
| 8 | 限流生效 | Postman 连击 `/api/verify/{code}` 31 次 → 看到 429 |
| 9 | HTTP→HTTPS | 浏览器输入 http://api.yourdomain.com 自动跳 https |
| 10 | 小程序扫码 | 真机扫生成的小程序码 → 能打开产品详情页 |
| 11 | 服务器域名白名单 | 小程序后台加 `https://api.yourdomain.com` / `https://h5.yourdomain.com` |
| 12 | CSV 禁止直链 | 直接访问 `/upload/csv/xxx.csv` 返回 403 |
| 13 | Nginx error.log | 上线后观察 10 分钟，无 499/502/504 |

## 十、升级与回滚

**升级流程**（后端 jar 更新为例）：

```bash
# 1. 上传新 jar 到 /www/wwwroot/product-trace/api/product-trace.jar.new
# 2. 备份
cp product-trace.jar product-trace.jar.bak.$(date +%Y%m%d%H%M%S)
# 3. 替换
mv product-trace.jar.new product-trace.jar
# 4. 重启
systemctl restart product-trace   # 或宝塔点「重启」
# 5. 验证
curl https://api.yourdomain.com/api/health
```

**回滚**：jar 版本不对就把 `.bak.*` 改回来再重启。

前端升级同理：新 dist 覆盖旧 dist，刷新浏览器（加 `?v=2` 强制缓存失效）即可。
