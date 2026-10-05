package ir.uciranx.ucg.core

/**
 * JNI bridge to hev-socks5-tunnel. Its JNI_OnLoad registers these four methods on this
 * exact class (built with -DPKGNAME=ir/uciranx/ucg/core -DCLSNAME=TProxyService), and
 * registration fails if any name or signature differs from src/hev-jni.c:
 *   TProxyStartService (Ljava/lang/String;I)Z
 *   TProxyStopService  ()Z
 *   TProxyIsRunning    ()Z
 *   TProxyGetStats     ()[J
 * The build workflow checks these signatures before building.
 */
object TProxyService {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic
    external fun TProxyStartService(configPath: String, fd: Int): Boolean

    @JvmStatic
    external fun TProxyStopService(): Boolean

    @JvmStatic
    external fun TProxyIsRunning(): Boolean

    @JvmStatic
    external fun TProxyGetStats(): LongArray?
}
