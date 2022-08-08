package mufanc.tools.aphelper

inline fun catch(block: () -> Unit) {
    try {
        block()
    } catch (err: Throwable) {
        Logger.e(err)
    }
}