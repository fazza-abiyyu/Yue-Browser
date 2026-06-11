import org.mozilla.geckoview.GeckoRuntimeSettings
fun main() {
    val builder = GeckoRuntimeSettings.Builder()
    builder.webExtensionsEnabled(true)
}
