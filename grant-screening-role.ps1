# 给「前缀拦截」授予系统的来电筛选（Caller ID & spam）角色。
#
# 背景：
#   ColorOS 15（一加 12 / PJD110 实测）实际上支持 android.app.role.CALL_SCREENING 角色，
#   但系统设置的「默认应用」里没有暴露「来电显示和骚扰拦截」这个入口，
#   所以只能通过 adb 直接授权。
#
#   系统更新、恢复出厂设置、或用手机管家清理后，这个授权可能失效，
#   届时重新运行本脚本即可。
#
# 用法：
#   1. 手机：设置 → 系统设置 → 开发者选项 → 打开「USB 调试」
#   2. 数据线连接电脑，手机上弹出「允许 USB 调试吗？」→ 勾选始终允许 → 确定
#   3. 在本文件所在目录执行：  .\grant-screening-role.ps1
#
# 本文件必须以 UTF-8 with BOM 保存：Windows PowerShell 5.1 会把无 BOM 的脚本按 GBK 读，
# 中文注释会被打乱并导致语法错误。

$ErrorActionPreference = 'Continue'

$adb = 'D:\DSH\android-sdk\platform-tools\adb.exe'
$package = 'com.dsh.prefixblocker'
$role = 'android.app.role.CALL_SCREENING'

if (-not (Test-Path $adb)) {
    Write-Host "找不到 adb：$adb" -ForegroundColor Red
    exit 1
}

$devices = & $adb devices | Where-Object { $_ -match "`tdevice" }
if (-not $devices) {
    Write-Host "没有检测到已授权的手机。" -ForegroundColor Red
    Write-Host "请检查："
    Write-Host "  - 开发者选项里的「USB 调试」是否已打开"
    Write-Host "  - 下拉通知栏，USB 连接方式是否改成了「传输文件」（不能是「仅充电」）"
    Write-Host "  - 数据线是否支持数据传输（有些线只有充电线芯）"
    Write-Host "  - 手机上是否点了「允许 USB 调试」"
    exit 1
}
Write-Host "已连接设备：$($devices -join ' | ')"

Write-Host ""
Write-Host "授权前的持有者："
& $adb shell cmd role get-role-holders $role

Write-Host ""
Write-Host "正在把角色授予 $package ..."
& $adb shell cmd role add-role-holder --user 0 $role $package
if ($LASTEXITCODE -ne 0) {
    Write-Host "授权失败（退出码 $LASTEXITCODE）—— 这台 ROM 可能禁用了该角色。" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "授权后的持有者："
& $adb shell cmd role get-role-holders $role

Write-Host ""
Write-Host "顺带加入电池优化白名单，避免被 ColorOS 后台冻结导致拦截失效 ..."
& $adb shell cmd deviceidle whitelist +$package

Write-Host ""
Write-Host "完成。打开「前缀拦截」，首页应显示「已获得来电筛选权限」。" -ForegroundColor Green
Write-Host "别忘了在界面上把要用的规则开关打开，否则不会有任何号码被拦截。"
