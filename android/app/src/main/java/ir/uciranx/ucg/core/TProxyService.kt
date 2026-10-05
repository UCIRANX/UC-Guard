package ir.uciranx.ucg.core

/**
 * JNI bridge to hev-socks5-tunnel. The native library registers these methods on
 * this exact class (built with -DPKGNAME=ir/uciranx/ucg/core -DCLASSNAME=TProxyService),
 * so the class name, package and signatures must not change.
 */
object TProxyService {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic
    external fun TProxyStartService(configPath: String, fd: Int)

    @JvmStatic
    external fun TProxyStopService()

    @JvmStatic
    external fun TProxyGetStats(): LongArray?
}
