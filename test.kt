import org.mozilla.geckoview.GeckoSession
fun main() {
    val methods = GeckoSession.ScrollDelegate::class.java.methods
    for (m in methods) {
        println("METHOD: \${m.name}")
    }
}
