package libv2ray

object Libv2ray {
    fun initCoreEnv(filesDir: String, logDir: String) {}
    fun newCoreController(handler: CoreCallbackHandler): CoreController {
        return CoreController()
    }
}
