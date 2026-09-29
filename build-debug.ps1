# 一键构建脚本：把本机的 JDK / Android SDK / Gradle 路径固定下来，避免每次手动设环境变量。
#
# 用法：
#   .\build-debug.ps1                     # 构建 debug APK
#   .\build-debug.ps1 testDebugUnitTest   # 跑匹配引擎单元测试
#   .\build-debug.ps1 clean assembleDebug
#
# 产物：app\build\outputs\apk\debug\app-debug.apk
#
# 本文件必须以 UTF-8 with BOM 保存：Windows PowerShell 5.1 会把无 BOM 的脚本按 GBK 读，
# 中文注释会被打乱并导致语法错误。

# 这里必须是 Continue 而不是 Stop：Gradle 经常往 stderr 写非致命告警
# （例如原生文件监视器不可用时的提示），用 Stop 会被当成致命错误直接中断脚本。
$ErrorActionPreference = 'Continue'

$env:JAVA_HOME = 'D:\DSH\jdk21'
$env:ANDROID_HOME = 'D:\DSH\android-sdk'
$env:ANDROID_SDK_ROOT = 'D:\DSH\android-sdk'
$env:ANDROID_USER_HOME = 'D:\DSH\android-sdk\.android'
$env:GRADLE_USER_HOME = 'D:\DSH\gradle-home'

$gradle = 'D:\DSH\gradle-8.7\bin\gradle.bat'
if (-not (Test-Path $gradle)) {
    Write-Host "找不到 Gradle：$gradle" -ForegroundColor Red
    Write-Host "请先下载 gradle-8.7-bin.zip 并解压到 D:\DSH\gradle-8.7"
    exit 1
}

$tasks = if ($args.Count -eq 0) { @('assembleDebug') } else { $args }
& $gradle -p $PSScriptRoot @tasks
exit $LASTEXITCODE
