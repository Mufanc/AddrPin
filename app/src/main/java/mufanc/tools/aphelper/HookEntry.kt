package mufanc.tools.aphelper

import android.net.LinkAddress
import android.os.Binder
import android.os.Build
import android.os.Parcel
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookEntry : IXposedHookLoadPackage {

    companion object {
        private const val PACKAGE_NAME = "com.android.networkstack.tethering.inprocess"
        private const val TRANSACT_CODE = 683262  // aka Mufanc
    }

    private var ipaddr = "192.168.137.1/24"

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            if (lpparam.packageName == PACKAGE_NAME) {
                Logger.i("load: ${lpparam.packageName}")
                hookRequestIpAddress(lpparam)
                hookTetheringOnTransact(lpparam)
            } else {
                Logger.i("skip: ${lpparam.packageName}")
            }
        } else {
            Logger.e("error: unsupported Android version!")
        }
    }

    // Hook IP 地址分配
    private fun hookRequestIpAddress(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            XposedHelpers.findClass("android.net.ip.IpServer", lpparam.classLoader),
            "requestIpv4Address",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    catch {
                        // Todo: 以迭代器方式检查调用栈?
                        if (Thread.currentThread().stackTrace.any {
                                it.methodName == "configureIPv4"
                            }) {
                            Logger.i("info: replaced hotspot IP address to $ipaddr")
                            param.result = LinkAddress::class.java.getDeclaredConstructor(String::class.java)
                                .newInstance(ipaddr)
                        } else {
                            Logger.i("skip: assigning downstream IP address")
                        }
                    }
                }
            }
        )
    }

    // Hook TetheringService 实现动态修改
    private fun hookTetheringOnTransact(lpparam: XC_LoadPackage.LoadPackageParam) {
        val stub = XposedHelpers.findClass("android.net.ITetheringConnector\$Stub", lpparam.classLoader)
        val descriptor = stub.getDeclaredField("DESCRIPTOR").apply { isAccessible = true }.get(null) as String

        XposedHelpers.findAndHookMethod(
            stub, "onTransact",
            Int::class.java, Parcel::class.java, Parcel::class.java, Int::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    catch {
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
                                Logger.i("info: new ipaddr $newIpaddr")
                                reply.writeString("ok")
                            } else {
                                throw Throwable()
                            }
                        } catch (err: Throwable) {
                            reply.writeString("invalid IP address.")
                        }

                        param.result = true
                    }
                }
            }
        )
    }
}
