# hev-socks5-tunnel registers its JNI methods on this class by name.
-keep class ir.uciranx.ucg.core.TProxyService { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
