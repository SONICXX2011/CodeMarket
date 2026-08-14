package ir.codemarket.app

object NativeLib {
    init {
        System.loadLibrary("codemarket_native")
    }
    external fun getBaseUrl(): String
    external fun buildLoginPayload(username: String, password: String): String
    external fun buildRegisterPayload(username: String, email: String, password: String): String
}