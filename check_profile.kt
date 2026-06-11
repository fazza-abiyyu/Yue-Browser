import org.mozilla.geckoview.GeckoRuntimeSettings
import java.io.File
fun main() {
    val builder = GeckoRuntimeSettings.Builder()
    builder.profile("default")
}
