package xyz.mufanc.apin

import android.annotation.SuppressLint
import android.net.LinkAddress
import android.os.SystemProperties
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker
import xyz.mufanc.autox.annotation.XposedEntry

@XposedEntry(["com.android.networkstack"])
class ModuleMain(
    private val ixp: XposedInterface,
    private val mlp: XposedModuleInterface.ModuleLoadedParam
) : XposedModule(ixp, mlp) {

    companion object {
        private const val TAG = "AddrPin"
        private const val TETHERING = "com.android.networkstack.tethering"
    }

    @SuppressLint("PrivateApi")
    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != TETHERING) {
            return
        }

        val ipServer = param.classLoader.loadClass("android.net.ip.IpServer")
        val requestIpv4Address = ipServer.declaredMethods.find { it.name == "requestIpv4Address" }!!

        ixp.hook(requestIpv4Address, RequestIpAddressHook::class.java)
    }

    @XposedHooker
    class RequestIpAddressHook : XposedInterface.Hooker {
        companion object {

            private fun replacedIpAddress(): String {
                return SystemProperties.get("debug.apin.ipaddr", "192.168.137.1/24")
            }

            @BeforeInvocation
            @JvmStatic
            fun handle(callback: BeforeHookCallback): RequestIpAddressHook? {
                val ipaddr = replacedIpAddress()
                val linkAddress = LinkAddress::class.java
                    .getDeclaredConstructor(String::class.java)
                    .newInstance(replacedIpAddress())

                Log.i(TAG, "replaced IP address to $ipaddr")

                callback.returnAndSkip(linkAddress)
                return null
            }
        }
    }
}
