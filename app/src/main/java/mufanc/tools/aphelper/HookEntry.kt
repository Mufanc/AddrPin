package mufanc.tools.aphelper

import android.net.LinkAddress
import android.os.Binder
import android.os.Build
import android.os.Parcel
import mufanc.easyhook.api.Logger
import mufanc.easyhook.api.annotation.XposedEntry
import mufanc.easyhook.api.hook.HookHelper
import mufanc.easyhook.api.hook.hook

@XposedEntry
class HookEntry: HookHelper("HotspotFix") {
    companion object {
        private const val TETHERING_PACKAGE = "com.android.networkstack.tethering.inprocess"
        private const val TRANSACT_CODE = 683262  // aka Mufanc
    }

    private var ipaddr = "192.168.137.1/24"

    override fun onHook() = handle {
        onLoadPackage(TETHERING_PACKAGE) { lpparam ->
            if (Build.VERSION.SDK_INT in setOf(Build.VERSION_CODES.R, Build.VERSION_CODES.S)) {
                Logger.i("Load: ${lpparam.packageName}")

                // Hook IP 地址分配
                findClass("android.net.ip.IpServer").hook {
                    method({ name == "requestIpv4Address" }) {
                        before { param ->
                            // Todo: 以迭代器方式检查调用栈?
                            if (Thread.currentThread().stackTrace.any { it.methodName == "configureIPv4" }) {
                                Logger.i("Info: replaced hotspot IP address to $ipaddr")
                                param.result = LinkAddress::class.java
                                    .getDeclaredConstructor(String::class.java)
                                    .newInstance(ipaddr)
                            } else {
                                Logger.i("Skip: assigning downstream IP address")
                            }
                        }
                    }
                }

                // Hook TetheringService 以实现动态修改
                findClass("android.net.ITetheringConnector\$Stub").hook {
                    method({ name == "onTransact" }) {
                        before {  param ->
                            if (param.args[0] != TRANSACT_CODE) return@before
                            if (Binder.getCallingUid() > 2000) return@before

                            val (data, reply) = arrayOf(param.args[1] as Parcel, param.args[2] as Parcel)
                            data.enforceInterface("android.net.ITetheringConnector")

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
            } else {
                Logger.e("Error: unsupported SDK version!")
            }
        }
    }
}
