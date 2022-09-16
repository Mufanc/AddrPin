package mufanc.tools.aphelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.LinkAddress
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.widget.Toast
import mufanc.easyhook.api.Logger
import mufanc.easyhook.api.Logger.Target
import mufanc.easyhook.api.annotation.XposedEntry
import mufanc.easyhook.api.hook.HookHelper
import mufanc.easyhook.api.hook.hook
import mufanc.easyhook.api.reflect.callMethodAs
import mufanc.easyhook.api.reflect.callStaticMethod

@XposedEntry
class HookEntry: HookHelper("HotspotFix") {
    companion object {
        private const val TETHERING_PACKAGE = "com.android.networkstack.tethering.inprocess"
    }

    private var ipaddr = "192.168.137.1/24"

    override fun onHook() = handle {
        Logger.configure(target = +Target.XPOSED_BRIDGE)

        if (Build.VERSION.SDK_INT in setOf(Build.VERSION_CODES.R, Build.VERSION_CODES.S)) {
            // Hook IP 地址分配
            onLoadPackage(TETHERING_PACKAGE) {
                findClass("android.net.ip.IpServer").hook {
                    method({ name == "requestIpv4Address" }) {
                        before { param ->
                            if (Thread.currentThread().stackTrace.any { it.methodName == "configureIPv4" }) {
                                Logger.i("replaced hotspot IP address to $ipaddr")
                                param.result = LinkAddress::class.java
                                    .getDeclaredConstructor(String::class.java)
                                    .newInstance(ipaddr)
                            } else {
                                Logger.d("assigning downstream IP address")
                            }
                        }
                    }
                }
            }

            // 注册广播接收器实现动态修改
            onLoadPackage("android") {
                val context: Context by lazy {
                    findClass("android.app.ActivityThread")
                        .callStaticMethod("currentActivityThread")!!
                        .callMethodAs("getSystemUiContext")!!
                }

                val handler by lazy {
                    Handler(HandlerThread("ApHelper").apply { start() }.looper)
                }

                findClass("com.android.server.am.ActivityManagerService").hook {
                    method({ name == "systemReady" }) {
                        after {
                            context.registerReceiver(
                                object : BroadcastReceiver() {
                                    override fun onReceive(context: Context, intent: Intent) {
                                        intent.getStringExtra("ipaddr")?.let {
                                            if (verifyIpAddress(it)) {
                                                ipaddr = it
                                                Logger.i("changed target ip address to: $it")
                                                handler.post {
                                                    Toast.makeText(context, "修改成功", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                },
                                IntentFilter("${BuildConfig.APPLICATION_ID}.IP_ADDRESS"),
                                "android.permission.INTERACT_ACROSS_USERS_FULL",
                                null
                            )
                            Logger.i("register receiver!")
                        }
                    }
                }
            }
        } else {
            Logger.e("unsupported SDK version!")
        }
    }

    private fun verifyIpAddress(address: String): Boolean {
        return try {
            val pattern = "(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)/(\\d+)".toRegex()
            val matches = pattern.find(address)!!.groupValues.drop(1)
            matches.drop(1).all { it.toInt() in 1..254 } && matches.last().toInt() in arrayOf(16, 24)
        } catch (ignored: Exception) {
            false
        }
    }
}
