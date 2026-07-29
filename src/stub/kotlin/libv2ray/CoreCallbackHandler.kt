package libv2ray

interface CoreCallbackHandler {
    fun startup(): Long
    fun shutdown(): Long
    fun onEmitStatus(code: Long, message: String?): Long
}
