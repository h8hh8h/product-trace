# ============================================================
# package-v1.3.0.ps1 - Local packaging script for v1.3.0 release (Windows PowerShell)
#
# Usage:
#   cd D:\Trae\product-trace\deploy
#   .\package-v1.3.0.ps1
#   # Optional:
#   .\package-v1.3.0.ps1 -ScanBaseUrl "https://h5.yourdomain.com/scan"
#
# Output dir: D:\Trae\product-trace\deploy\release-v1.3.0\
#   Contains: product-trace-v1.3.0.jar / admin-dist-v1.3.0.zip /
#             migration-v3~v6.sql / migration-v6-fix.sql /
#             deploy-v1.3.0.sh / backup.sh
# Upload the whole release-v1.3.0 dir to /root/product-trace-v1.3.0/ on server.
# ============================================================
[CmdletBinding()]
param(
    [string]$ScanBaseUrl = "https://h5.yourdomain.com/scan"
)

$ErrorActionPreference = "Continue"
$env:Path = "C:\Program Files\nodejs;$env:Path"
$root   = "D:\Trae\product-trace"
$deploy = "$root\deploy"
$out    = "$deploy\release-v1.3.0"

$mvn  = "C:\tools\apache-maven-3.9.9\bin\mvn.cmd"
$npm  = "C:\Program Files\nodejs\npm.cmd"
$java = "java"

function Step($msg) { Write-Host ""; Write-Host "===== $msg =====" -ForegroundColor Cyan }
function Ok($msg)   { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Die($msg)  { Write-Host "  [FAIL] $msg" -ForegroundColor Red; exit 1 }

# 0. Env check
Step "Env check"
foreach ($tool in @($mvn, $npm)) { if (-not (Test-Path $tool)) { Die "Tool not found: $tool" } }
$javaVer = (cmd /c "java -version 2>&1") -join "`n" | Select-Object -First 1
Write-Host "  $javaVer"
$free = (Get-PSDrive D).Free
if ($free -lt 1GB) { Die "Drive D has less than 1GB free" }
Ok "Env OK"

# 1. Stop local 8080 to avoid jar lock
Step "Stop any process on port 8080"
$conn = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($conn) {
    $p = $conn | Select-Object -First 1 -ExpandProperty OwningProcess
    Stop-Process -Id $p -Force
    Start-Sleep -Seconds 1
    Ok "Stopped PID $p"
} else {
    Ok "Port 8080 is free"
}

# 2. Clean output dir
Step "Clean output dir"
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Path $out | Out-Null
Ok "Output dir: $out"

# 3. Build backend (with 48 unit tests)
Step "Backend build (mvn clean package, with unit tests)"
Push-Location $root
try {
    & $mvn clean package -q
    if ($LASTEXITCODE -ne 0) { Die "mvn build failed (exit=$LASTEXITCODE)" }
} finally {
    Pop-Location
}
$jar = "$root\target\product-trace.jar"
if (-not (Test-Path $jar)) { Die "Artifact not found: $jar" }
$jarSize = [math]::Round((Get-Item $jar).Length / 1MB, 1)
Ok "Backend jar generated: ${jarSize}MB"

# 4. Build frontend (inject scan-base-url)
Step "Frontend build (admin-web)"
$env:VITE_SCAN_BASE_URL = $ScanBaseUrl
Push-Location "$root\admin-web"
try {
    & $npm run build
    if ($LASTEXITCODE -ne 0) { Die "npm run build failed (exit=$LASTEXITCODE)" }
} finally {
    Pop-Location
}
$dist = "$root\admin-web\dist"
if (-not (Test-Path "$dist\index.html")) { Die "Frontend dist missing index.html" }
$distZip = "$out\admin-dist-v1.3.0.zip"
if (Test-Path $distZip) { Remove-Item $distZip -Force }
Compress-Archive -Path "$dist\*" -DestinationPath $distZip
Ok "Frontend zip generated: $([math]::Round((Get-Item $distZip).Length / 1KB, 0))KB"

# 5. Copy files to output dir
Step "Assemble release package"
Copy-Item $jar "$out\product-trace-v1.3.0.jar" -Force
foreach ($f in @("migration-v3.sql","migration-v4.sql","migration-v5.sql","migration-v6.sql","migration-v6-fix.sql")) {
    $p = "$deploy\$f"
    if (Test-Path $p) { Copy-Item $p $out -Force } else { Die "Missing $f" }
}
foreach ($f in @("deploy-v1.3.0.sh","backup.sh")) {
    $p = "$deploy\$f"
    if (Test-Path $p) { Copy-Item $p $out -Force } else { Write-Host "  [WARN] optional file not included: $f" -ForegroundColor Yellow }
}
Ok "All files copied to $out"

# 6. Verify artifacts
Step "Verify artifacts"
$files = Get-ChildItem $out | Sort-Object Name
foreach ($f in $files) {
    $sz = if ($f.Length -gt 1MB) { "$([math]::Round($f.Length/1MB,1))MB" } else { "$([math]::Round($f.Length/1KB,0))KB" }
    Write-Host ("  {0,-30} {1}" -f $f.Name, $sz)
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead($distZip)
$names = $z.Entries | ForEach-Object FullName
$z.Dispose()
$have = $names -join ','
@("index.html","CodePrint","Role-","AdminUser","AuditLog","ScanStats") | ForEach-Object {
    if ($have -match $_) { Ok "Frontend contains $_" } else { Die "Frontend missing $_" }
}

Step "Done"
Write-Host ""
Write-Host "Package ready. Upload dir to server:" -ForegroundColor Green
Write-Host "  scp -r $out root@your-server:/root/product-trace-v1.3.0"
Write-Host ""
Write-Host "On server run:" -ForegroundColor Green
Write-Host "  cd /root/product-trace-v1.3.0"
Write-Host "  sed -i 's/\r\`$//' deploy-v1.3.0.sh"
Write-Host "  bash -n deploy-v1.3.0.sh"
Write-Host "  ./deploy-v1.3.0.sh"
Write-Host ""
