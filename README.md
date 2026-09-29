# 前缀拦截（PrefixCallBlocker）

一个 Android 来电拦截 App：**按号码前缀（或包含、正则）匹配，命中后自动挂断或静音**。
基于系统原生的 `CallScreeningService`（Android 7.0 / API 24 起），系统会在来电**响铃之前**把号码交给本应用判定。

典型用途：屏蔽 `400` 开头的企业客服号、`95` 开头的银行短号、某个固定号段、某个国家/地区代码。

---

## 功能

| 能力 | 说明 |
| --- | --- |
| 前缀匹配 | 例如 `400`、`95`、`010`、`138`、`+86` |
| 包含 / 正则匹配 | 例如包含 `5588`，或正则 `^1[3-9]\d{9}$` |
| 三种动作 | 拦截挂断 / 静音拦截 / 白名单放行 |
| 号码归一化 | `+8613800138000`、`8613800138000`、`13800138000`、`0086138000138000`、`010-1234 5678` 都能被同一条 `138` / `010` 规则命中 |
| 优先级 | 白名单 > 最具体的规则（模式最长）> 先定义的规则 |
| 隐藏号码 | 可单独开关是否拦截「未知号码」来电 |
| 测试模式 | 只写记录不真正拦截，用来先观察几天有没有误伤 |
| 拦截记录 | 最近 300 条，含命中规则与时间 |
| 总开关 | 一键全部放行 |

**隐私**：判定只用号码文本，不读通讯录、不联网、不申请任何权限。

---

## 目录结构

```
PrefixCallBlocker/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties     Gradle 8.7
├── tools/check-rule-engine.mjs                  无 SDK 环境下的算法自检脚本
└── app/
    ├── build.gradle.kts                         AGP 8.5.2 / Kotlin 1.9.24 / compileSdk 34 / minSdk 24
    └── src/
        ├── main/AndroidManifest.xml
        ├── main/java/com/dsh/prefixblocker/
        │   ├── PhoneNumberUtils.kt              号码归一化与候选形式
        │   ├── CallRule.kt                      规则模型 + JSON 序列化
        │   ├── RuleEngine.kt                    匹配引擎（纯逻辑，可单测）
        │   ├── BlockerScreeningService.kt       ★ 来电筛选服务
        │   ├── RuleStore.kt / SettingsStore.kt / BlockLogStore.kt   本地存储
        │   ├── MainActivity.kt / LogActivity.kt
        │   ├── RuleListAdapter.kt / LogListAdapter.kt / Labels.kt
        │   └── ...
        ├── main/res/                            布局、字符串、主题、图标
        └── test/java/com/dsh/prefixblocker/     RuleEngineTest.kt、PrefixFormatTest.kt
```

---

## 编译

### 开发环境

> 下面这套是**作者机器上的实际配置，仅作参考**。换机器只要装好 Android SDK + JDK 17～21、
> 改一下 `local.properties` 里的 `sdk.dir` 即可；`build-debug.ps1` 和 `grant-screening-role.ps1`
> 里有绝对路径，需要改成你自己的。

| 组件 | 位置 | 版本 |
| --- | --- | --- |
| Android SDK | `D:\DSH\android-sdk` | platform-tools 37.0.1、platforms;android-34、build-tools;34.0.0、cmdline-tools latest |
| JDK | `D:\DSH\jdk21` | Temurin 21.0.12.1 LTS |
| Gradle | `D:\DSH\gradle-8.7` | 8.7 |
| Gradle 缓存 | `D:\DSH\gradle-home` | — |

`local.properties` 里的 `sdk.dir` 指向 SDK 位置（该文件不入库）；`gradle/wrapper/` 下的 wrapper
文件（含 `gradle-wrapper.jar`）已入库，可以直接 `.\gradlew.bat`。

> **JDK 版本注意**：Gradle 8.7 支持 JDK 17～21，**不支持 Java 24**。系统 JDK 太新的话，
> 需要另装一个 JDK 21 并设置 `JAVA_HOME`。

### 方式 A：一键脚本（推荐）

```powershell
cd D:\DSH\PrefixCallBlocker
.\build-debug.ps1                     # 构建 debug APK
.\build-debug.ps1 testDebugUnitTest   # 跑单元测试
```

脚本内部就是把路径设好再调 Gradle：

```powershell
$env:JAVA_HOME='D:\DSH\jdk21'
$env:ANDROID_HOME='D:\DSH\android-sdk'
$env:GRADLE_USER_HOME='D:\DSH\gradle-home'
D:\DSH\gradle-8.7\bin\gradle.bat -p D:\DSH\PrefixCallBlocker assembleDebug
```

产物：`app\build\outputs\apk\debug\app-debug.apk`

### 方式 B：Android Studio

Android Studio Hedgehog (2023.1.1) 或更新版本 → **Open** → 选择 `PrefixCallBlocker` 目录 → Sync → ▶ Run。
Android Studio 自带 JDK，不依赖上面那个 JDK 21；`local.properties` 会让它直接使用已装好的 SDK。

### 方式 C：gradlew（记得先设 JAVA_HOME）

```powershell
$env:JAVA_HOME='D:\DSH\jdk21'
.\gradlew.bat assembleDebug
```

---

## 安装后必须做的一步

**光装上不会生效。** 必须把本应用设为系统的来电筛选应用：

- **Android 10 及以上**：打开 App → 点「去授权」→ 在系统弹窗里选择「前缀拦截」
- 或者手动：`设置 → 应用 → 默认应用 → 来电显示和骚扰拦截` → 选择「前缀拦截」
- **Android 7～9**：`设置 → 应用 → 默认应用 → 来电显示和骚扰拦截`

App 首页的状态卡片会显示当前授权状态；已授权时按钮自动隐藏。

---

## 使用建议

1. 先打开 **「测试模式（只记录不拦截）」**
2. 添加规则，正常使用几天，去「查看拦截记录」确认没有误伤正常来电
3. 确认没问题后关闭测试模式，拦截立即生效

常用规则示例：

| 目标 | 匹配方式 | 模式 |
| --- | --- | --- |
| 400 开头的企业客服号 | 开头匹配 | `400` |
| 95 开头的银行/客服短号 | 开头匹配 | `95` |
| 所有中国大陆来电 | 开头匹配 | `+86` |
| 某个手机号段 | 开头匹配 | `138` |
| 所有手机号 | 正则 | `^1[3-9]\d{9}$` |
| 号码里含 5588 的 | 包含 | `5588` |

规则行左侧的开关可以单独停用某条规则；长按或点铅笔图标可编辑，垃圾桶图标删除。

---

## 匹配规则说明（重要）

- 非正则模式下，规则会被清理成纯号码（空格、横线、括号都会被去掉）；正则表达式原样保留。
- 来电号码会展开成多个候选形式后逐个比对：

  1. `+8613800138000`（归一化结果）
  2. `8613800138000`（去掉 `+`）
  3. `13800138000`（去掉国家码的国内形式）

- 第 3 种形式**要求规则至少 2 位**。所以规则 `1` 不会误伤国内号码（`+8613123456789` 放行），但仍能正常命中 `+1` 的号码。
- 优先级：**白名单 > 模式更长的规则 > 先定义的规则**。
- 号码来自系统提供的 Caller ID；运营商在云端就拦掉的号码不会到达 App。

---

## 已知限制（务必看）

- **部分国产 ROM 会限制第三方来电筛选应用**（MIUI / EMUI / ColorOS / OriginOS 等）：可能无法授予权限，或授权后不生效。这类机型建议直接用系统自带的骚扰拦截。
- Android 9 及以下没有 Role API，App 无法自行查询是否已授权，状态卡片会一直显示「未授权」，请手动到「默认应用」里确认。
- iOS 不支持按前缀拦截，系统只支持整号码黑名单。
- 使用第三方拨号器 / 双卡 VoLTE 时行为可能有差异。
- 系统对「静音拦截」的处理（是否记入通话记录、是否弹通知）在不同 Android 版本上略有差别。

---

## 测试

**匹配引擎单元测试**（纯 JVM，不需要设备）：

```
gradlew :app:testDebugUnitTest
```

或直接在 Android Studio 里右键 `app/src/test/java/com/dsh/prefixblocker/RuleEngineTest.kt` → Run。

本机实测（Gradle 8.7 + JDK 21）：

```
com.dsh.prefixblocker.RuleEngineTest
用例 13 / 失败 0 / 错误 0 / 跳过 0，耗时 0.076 秒
```

覆盖归一化、前缀/包含/正则、白名单优先级、最长模式优先、隐藏号码、非法正则等场景。

**无 Android SDK 时的算法自检**（同一套逻辑的 JS 等价实现）：

```bash
node tools/check-rule-engine.mjs
```

没有编译器时可用来快速验证算法行为，本机运行结果 **29/29 通过**。

---

## 验证程度

**已在本机实测通过**（Gradle 8.7 + JDK 21 + Android SDK 34）：

- `assembleDebug` → **BUILD SUCCESSFUL in 4m 40s**，产出 `app\build\outputs\apk\debug\app-debug.apk`（5.41 MB）
- `processDebugResources` 通过 —— 全部 XML 资源经 AAPT2 校验合法
- `compileDebugKotlin` / `compileDebugJavaWithJavac` 通过 —— 12 个 Kotlin 文件与 ViewBinding 生成代码全部编译成功
- `testDebugUnitTest` 通过 —— 匹配引擎单元测试 **13/13**
- APK 清单经 `aapt2 dump badging` 校验：包名 `com.dsh.prefixblocker`、minSdk 24、targetSdk 34，
  `BlockerScreeningService` 带 `BIND_SCREENING_SERVICE` 权限与 `android.telecom.CallScreeningService` intent-filter

**尚未验证的：**

- 真机上的实际拦截效果 —— 本机没有连接 Android 手机，也没有安装模拟器系统镜像
- release 签名构建 —— 目前产出的是 debug 签名 APK（安装测试没问题，上架需自行配签名）

装到手机上：

```powershell
# 数据线连接并打开 USB 调试后
D:\DSH\android-sdk\platform-tools\adb.exe install -r D:\DSH\PrefixCallBlocker\app\build\outputs\apk\debug\app-debug.apk
```

或直接把 `app-debug.apk` 传到手机点击安装（需允许「安装未知来源应用」）。
