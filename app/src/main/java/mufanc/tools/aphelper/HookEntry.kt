package mufanc.tools.aphelper

import android.net.LinkAddress
import android.os.Build
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "HotspotFix"
        private const val PACKAGE_NAME = "com.android.networkstack.tethering.inprocess"
        private const val IPADDR = "192.168.137.1/24"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                if (lpparam.packageName == PACKAGE_NAME) {
                    XposedHelpers.findAndHookMethod(
                        XposedHelpers.findClass("android.net.ip.IpServer", lpparam.classLoader),
                        "requestIpv4Address",
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                try {
                                    if (Thread.currentThread().stackTrace.any {
                                        it.methodName == "configureIPv4"
                                    }) {
                                        param.result = LinkAddress::class.java.getDeclaredConstructor(String::class.java)
                                            .newInstance(IPADDR)
                                    }
                                } catch (err: Throwable) {
                                    Log.e(TAG, "", err)
                                }
                            }
                        }
                    )
                }
            } else {
                Log.e(TAG, "unsupported Android version!")
            }
        } catch (err: Throwable) {
            Log.e(TAG, "", err)
        }
    }
}
