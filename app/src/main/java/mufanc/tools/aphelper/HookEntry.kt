package mufanc.tools.aphelper

import android.net.LinkAddress
import android.os.Binder
import android.os.Build
import android.os.Parcel
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "HotspotFix"
        private const val PACKAGE_NAME = "com.android.networkstack.tethering.inprocess"
        private const val TRANSACT_CODE = 683262  // aka Mufanc
    }

    private var ipaddr = "192.168.137.1/24"

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                if (lpparam.packageName == PACKAGE_NAME) {
                    // Hook IP 地址分配
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
                                            .newInstance(ipaddr)
                                    }
                                } catch (err: Throwable) {
                                    Log.e(TAG, "", err)
                                }
                            }
                        }
                    )

                    // Hook TetheringService 实现动态修改
                    val stub = XposedHelpers.findClass("android.net.ITetheringConnector\$Stub", lpparam.classLoader)
                    val descriptor = stub.getDeclaredField("DESCRIPTOR").apply { isAccessible = true }.get(null) as String

                    XposedHelpers.findAndHookMethod(
                        stub, "onTransact",
                        Int::class.java, Parcel::class.java, Parcel::class.java, Int::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                try {
                                    if (param.args[0] != TRANSACT_CODE) return
                                    if (Binder.getCallingUid() > 2000) return

                                    val (data, reply) = arrayOf(param.args[1] as Parcel, param.args[2] as Parcel)

                                    data.enforceInterface(descriptor)

                                    try {
                                        val newIpaddr = data.readString()!!
                                        val pattern = "(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)/(\\d+)".toRegex()

                                        val matches = pattern.find(newIpaddr)!!.groupValues.drop(1)
                                        if (matches.drop(1).all { it.toInt() in 1..254 } && matches.last().toInt() in arrayOf(16, 24) ) {
                                            ipaddr = newIpaddr
                                            Log.i(TAG, "ipaddr: $newIpaddr, uid: ${Binder.getCallingUid()}")
                                            reply.writeString("OK")
                                        } else {
                                            throw Throwable()
                                        }
                                    } catch (err: Throwable) {
                                        reply.writeString("Invalid IP address.")
                                    }

                                    param.result = true
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
