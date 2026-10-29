# ============================================================
# package-final.ps1 — 全量整合版打包脚本（Windows PowerShell）
#
# 用法：
#   cd D:\Trae\product-trace\deploy
#   .\package-final.ps1
#   # 可选参数（会注入到 admin 构建，用于二维码短链预览）：
#   .\package-final.ps1 -ScanBaseUrl "https://h5.你的域名.com/scan"
#
# 产出：D:\Trae\product-trace\deploy\release-final\
#   product-trace.jar          后端（含全部单测）
#   admin-dist.zip             管理后台 dist
#   h5-dist.zip                用户端 H5 dist
#   mp-weixin-dist.zip         微信小程序产物
#   init-all.sql               全量建库脚本
#   application-prod.yml       生产配置模板
#   nginx-full-stack.conf      三站点 Nginx 配置
#   backup.sh                  定时备份脚本
#   deploy-final.sh            服务器一键部署/回滚脚本
#   从零一步到位部署手册.md      部署手册
#
# 上传：把 release-final 整个目录 scp 到服务器 /root/product-trace-final/
#       （mp-weixin-dist.zip 不需上传服务器，直接导入微信开发者工具）
# ============================================================
[CmdletBinding()]
param(
    [string]$ScanBaseUrl = "https://h5.zjknitting.com/scan"
)

$ErrorActionPreference = "Continue"
$env:Path = "C:\Program Files\nodejs;$env:Path"
$root   = "D:\Trae\product-trace"
$deploy = "$root\deploy"
$out    = "$deploy\release-final"

$mvn = "C:\tools\apache-maven-3.9.9\bin\mvn.cmd"
$npm = "C:\Program Files\nodejs\npm.cmd"

function Step($msg) { Write-Host ""; Write-Host "===== $msg =====" -ForegroundColor Cyan }
function Ok($msg)   { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Die($msg)  { Write-Host "  [FAIL] $msg" -ForegroundColor Red; exit 1 }

# 0. 环境检查
Step "Env check"
foreach ($tool in @($mvn, $npm)) { if (-not (Test-Path $tool)) { Die "Tool not found: $tool" } }
$javaVer = (cmd /c "java -version 2>&1") -join "`n" | Select-Object -First 1
Write-Host "  $javaVer"
Ok "Env OK"

# 1. 停掉本地 8080，避免 jar 被占用
Step "Stop process on port 8080 if any"
$conn = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($conn) {
    $p = $conn | Select-Object -First 1 -ExpandProperty OwningProcess
    Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
    Ok "Stopped PID $p"
} else { Ok "Port 8080 is free" }

# 2. 清理输出目录
Step "Clean output dir"
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Path $out | Out-Null
Ok "Output dir: $out"

# 3. 后端构建（含单测）
Step "Backend build (mvn clean package, with unit tests)"
Push-Location $root
try {
    & $mvn clean package -q
    if ($LASTEXITCODE -ne 0) { Die "mvn build failed (exit=$LASTEXITCODE)" }
} finally { Pop-Location }
$jar = "$root\target\product-trace.jar"
if (-not (Test-Path $jar)) { Die "Artifact not found: $jar" }
Ok "Backend jar: $([math]::Round((Get-Item $jar).Length / 1MB, 1))MB"

# 4. 管理后台构建（注入扫码前缀用于标签打印预览）
Step "Frontend build (admin-web)"
$env:VITE_SCAN_BASE_URL = $ScanBaseUrl
Push-Location "$root\admin-web"
try {
    & $npm run build
    if ($LASTEXITCODE -ne 0) { Die "admin-web build failed (exit=$LASTEXITCODE)" }
} finally { Pop-Location }
if (-not (Test-Path "$root\admin-web\dist\index.html")) { Die "admin dist missing index.html" }
Compress-Archive -Path "$root\admin-web\dist\*" -DestinationPath "$out\admin-dist.zip" -Force
Ok "admin-dist.zip: $([math]::Round((Get-Item "$out\admin-dist.zip").Length / 1KB, 0))KB"

# 5. H5 构建
Step "Frontend build (uni-app H5)"
Push-Location "$root\frontend-uniapp"
try {
    & $npm run build:h5
    if ($LASTEXITCODE -ne 0) { Die "H5 build failed (exit=$LASTEXITCODE)" }
} finally { Pop-Location }
if (-not (Test-Path "$root\frontend-uniapp\dist\build\h5\index.html")) { Die "H5 dist missing index.html" }
Compress-Archive -Path "$root\frontend-uniapp\dist\build\h5\*" -DestinationPath "$out\h5-dist.zip" -Force
Ok "h5-dist.zip: $([math]::Round((Get-Item "$out\h5-dist.zip").Length / 1KB, 0))KB"

# 6. 微信小程序构建
Step "Frontend build (uni-app mp-weixin)"
Push-Location "$root\frontend-uniapp"
try {
    & $npm run build:mp-weixin
    if ($LASTEXITCODE -ne 0) { Die "mp-weixin build failed (exit=$LASTEXITCODE)" }
} finally { Pop-Location }
Compress-Archive -Path "$root\frontend-uniapp\dist\build\mp-weixin\*" -DestinationPath "$out\mp-weixin-dist.zip" -Force
Ok "mp-weixin-dist.zip: $([math]::Round((Get-Item "$out\mp-weixin-dist.zip").Length / 1KB, 0))KB"

# 7. 组装交付物
Step "Assemble release package"
Copy-Item $jar "$out\product-trace.jar" -Force
foreach ($f in @("init-all.sql", "application-prod.yml", "nginx-full-stack.conf", "backup.sh", "deploy-final.sh")) {
    $src = switch ($f) {
        "init-all.sql"           { "$deploy\init-all.sql" }
        "application-prod.yml"   { "$root\src\main\resources\application-prod.yml" }
        "nginx-full-stack.conf"  { "$deploy\nginx\full-stack.conf" }
        default                  { "$deploy\$f" }
    }
    if (Test-Path $src) { Copy-Item $src "$out\$f" -Force; Ok "copied $f" }
    else { Write-Host "  [WARN] missing $f" -ForegroundColor Yellow }
}
# 部署手册
$manual = "$deploy\release-final\从零一步到位部署手册.md"
if (Test-Path $manual) { Copy-Item $manual "$out\从零一步到位部署手册.md" -Force; Ok "copied manual" }
# 去掉 Windows 换行符，便于 Linux 直接执行
if (Test-Path "$out\deploy-final.sh") {
    (Get-Content "$out\deploy-final.sh" -Raw) -replace "`r`n", "`n" | Set-Content "$out\deploy-final.sh" -NoNewline -Encoding utf8
    Ok "deploy-final.sh: CRLF -> LF"
}

# 8. 校验产物
Step "Verify artifacts"
Get-ChildItem $out | Sort-Object Name | ForEach-Object {
    $sz = if ($_.Length -gt 1MB) { "$([math]::Round($_.Length / 1MB, 1))MB" } else { "$([math]::Round($_.Length / 1KB, 0))KB" }
    Write-Host ("  {0,-28} {1}" -f $_.Name, $sz)
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead("$out\admin-dist.zip")
$names = ($z.Entries | ForEach-Object FullName) -join ','
$z.Dispose()
@("index.html", "StorageConfig", "CodePrint", "AdminUser", "AuditLog", "ScanStats") | ForEach-Object {
    if ($names -match $_) { Ok "admin dist contains $_" } else { Die "admin dist missing $_" }
}

Step "Done"
Write-Host ""
Write-Host "Package ready. Upload to server:" -ForegroundColor Green
Write-Host "  scp -r $out root@<server>:/root/product-trace-final"
Write-Host ""
Write-Host "On server run:" -ForegroundColor Green
Write-Host "  cd /root/product-trace-final"
Write-Host "  sed -i 's/\r\`$//' deploy-final.sh && bash -n deploy-final.sh"
Write-Host "  ./deploy-final.sh"
Write-Host ""
