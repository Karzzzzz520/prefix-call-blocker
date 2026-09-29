# 默认规则即可。CallScreeningService 由系统通过 AndroidManifest 反射实例化，
# 因此保留服务类名与构造方法。
-keep class com.dsh.prefixblocker.BlockerScreeningService { *; }
