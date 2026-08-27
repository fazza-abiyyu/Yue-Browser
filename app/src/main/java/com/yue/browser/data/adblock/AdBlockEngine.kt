package com.yue.browser.data.adblock

import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.yue.browser.data.adblock.config.AdBlockPolicy
import com.yue.browser.data.adblock.config.CookieBannerPolicy
import com.yue.browser.data.adblock.config.Decision
import com.yue.browser.data.adblock.config.DecisionAction
import com.yue.browser.data.adblock.config.DocumentContext
import com.yue.browser.data.adblock.config.NetworkPolicy
import com.yue.browser.data.adblock.config.NavigationPolicy
import com.yue.browser.data.adblock.config.RequestContext
import com.yue.browser.data.adblock.config.ResourceType
import com.yue.browser.data.adblock.config.RiskScoringPolicy
import com.yue.browser.data.adblock.config.WebSocketPolicy
import com.yue.browser.data.adblock.config.StrategyStore
import com.yue.browser.data.adblock.engine.AntiAdblockBypass
import com.yue.browser.data.adblock.engine.CookieBannerRemover
import com.yue.browser.data.adblock.engine.CosmeticFilterEngine
import com.yue.browser.data.adblock.engine.HostBlocker
import com.yue.browser.data.adblock.engine.KeywordDetector
import com.yue.browser.data.adblock.engine.PopupDetector
import com.yue.browser.data.adblock.engine.RedirectDetector
import com.yue.browser.data.adblock.engine.WebSocketBlocker
import com.yue.browser.data.adblock.engine.WhitelistChecker
import com.yue.browser.data.adblock.pipeline.StrategyPipeline
import com.yue.browser.data.adblock.remote.StrategyApi
import com.yue.browser.data.adblock.service.AdBlockStats
import com.yue.browser.data.adblock.service.BlockingService
import com.yue.browser.data.adblock.service.CosmeticService
import com.yue.browser.data.adblock.service.RedirectService
import com.yue.browser.data.adblock.service.YouTubeService
import com.yue.browser.data.adblock.sync.FilterSyncEngine
import com.yue.browser.domain.model.BrowserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * AdBlockEngine — Facade entry point for the Yue AdBlock Engine.
 *
 * Architecture:
 *   Strategy decides, rules describe, context explains, engine executes.
 *
 * Layers:
 *   L0: AdBlockEngine (Facade)
 *   L1: Services (BlockingService, RedirectService, CosmeticService, YouTubeService)
 *   L2: Pipeline + DecisionEngine
 *   L3: Engine Primitives (HostBlocker, WebSocketBlocker, CookieBannerRemover, ...)
 */
object AdBlockEngine {
    private const val TAG = "AdBlockEngine"
    private const val PREFS_NAME = "adblock_sync_prefs"
    private const val KEY_LAST_SYNC = "last_sync_timestamp"

    // Engine primitives
    private val hostBlocker = HostBlocker()
    private val whitelistChecker = WhitelistChecker()
    private val keywordDetector = KeywordDetector()
    private val redirectDetector = RedirectDetector()
    private val popupDetector = PopupDetector()
    private val cosmeticEngine = CosmeticFilterEngine()
    private val webSocketBlocker = WebSocketBlocker()
    private val antiAdblockBypass = AntiAdblockBypass()
    private val cookieBannerRemover = CookieBannerRemover()

    // Pipeline
    private val pipeline = StrategyPipeline(
        hostBlocker = hostBlocker,
        whitelistChecker = whitelistChecker,
        keywordDetector = keywordDetector,
        redirectDetector = redirectDetector,
        popupDetector = popupDetector,
        webSocketBlocker = webSocketBlocker,
        antiAdblockBypass = antiAdblockBypass,
    )

    // Services
    private val blockingService = BlockingService(hostBlocker, whitelistChecker, keywordDetector)
    private val redirectService = RedirectService(blockingService)
    private val cosmeticService = CosmeticService(cosmeticEngine)
    private val youTubeService = YouTubeService()

    // Stats & analytics
    private val stats = AdBlockStats()

    // Sync & config
    private lateinit var store: StrategyStore
    private lateinit var api: StrategyApi
    private lateinit var syncEngine: FilterSyncEngine

    // Active policy
    private var activePolicy: AdBlockPolicy? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Profiling
    private var lastProfile: String = "balanced"

    // Sync cooldown — only sync when app opens and last sync was >6h ago
    private val syncCooldownMs = 6 * 60 * 60 * 1000L // 6 hours
    private val prefs by lazy {
        getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var appContext: Context? = null

    private fun getApplicationContext(): Context {
        return appContext ?: throw IllegalStateException("AdBlockEngine not initialized")
    }

    private fun shouldSyncOnOpen(): Boolean {
        val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
        val now = System.currentTimeMillis()
        return lastSync == 0L || (now - lastSync) >= syncCooldownMs
    }

    private fun markSyncedNow() {
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }

    // ========================================================================
    // Whitelist / allowed domains
    // ========================================================================
    val whitelistHosts: Set<String>
        get() = whitelistChecker.getWhitelistedHosts()

    // ========================================================================
    // Stats access
    // ========================================================================
    fun getStats(): AdBlockStats = stats

    // ========================================================================
    // Initialization
    // ========================================================================

    fun initialize(context: Context) {
        Log.d(TAG, "Initializing AdBlockEngine v2...")

        appContext = context.applicationContext
        store = StrategyStore(context)
        api = StrategyApi(context)
        syncEngine = FilterSyncEngine(context, api, store)

        activePolicy = store.loadActivePolicy() ?: loadDefaultPolicy()

        loadAssetRules(context)

        loadCookieBannerSelectors(context)

        loadGeneralAdSelectors()

        if (shouldSyncOnOpen()) {
            Log.d(TAG, "Last sync >6h ago, triggering sync on app open")
            scope.launch {
                val result = syncEngine.sync(lastProfile)
                when (result) {
                    is FilterSyncEngine.SyncResult.Success -> {
                        activePolicy = result.policy
                        loadLocalRules(context)
                        markSyncedNow()
                        Log.d(TAG, "Sync completed on open: policy v${result.policy.policyVersion}")
                    }
                    is FilterSyncEngine.SyncResult.Failure -> {
                        Log.w(TAG, "Sync failed on open: ${result.message}")
                    }
                    is FilterSyncEngine.SyncResult.Skipped -> {
                        Log.d(TAG, "Sync skipped on open: ${result.reason}")
                    }
                }
            }
        } else {
            val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
            val remainingMs = syncCooldownMs - (System.currentTimeMillis() - lastSync)
            val remainingH = remainingMs / (60 * 60 * 1000)
            Log.d(TAG, "Sync skipped on open (cooldown: ${remainingH}h remaining)")
        }

        Log.d(TAG, "AdBlockEngine initialized: hosts=${hostBlocker.size()}")
    }

    // ========================================================================
    // Core blocking API
    // ========================================================================

    fun shouldBlock(context: RequestContext): Decision {
        val policy = activePolicy ?: return Decision(
            action = DecisionAction.ALLOW,
            totalScore = 0,
            reasons = emptyList(),
        )

        val startTime = System.currentTimeMillis()
        val decision = pipeline.execute(context, policy)
        val latency = System.currentTimeMillis() - startTime

        // Record stats
        when (decision.action) {
            DecisionAction.BLOCK -> {
                stats.recordBlocked(
                    host = context.host,
                    signalType = decision.reasons.firstOrNull()?.signalType?.name,
                    latencyMs = latency,
                )
            }
            DecisionAction.WARN -> stats.recordWarning()
            DecisionAction.ALLOW -> stats.recordAllowed()
        }

        return decision
    }

    fun getCosmeticCSS(url: String): String {
        return cosmeticService.getCosmeticCSS(url, activePolicy)
    }

    // ========================================================================
    // Utility blocking checks
    // ========================================================================

    fun isAdblockActive(settings: BrowserSettings?): Boolean {
        return blockingService.isAdblockActive(settings)
    }

    fun isHostBlocked(context: Context, host: String, settings: BrowserSettings?): Boolean {
        val result = blockingService.isHostBlocked(context, host, settings)
        if (result) {
            stats.recordBlocked(host, "HOST_BLOCK", 0)
        }
        return result
    }

    fun isJudolHost(context: Context, host: String): Boolean {
        return blockingService.isJudolHost(context, host)
    }

    fun isCustomFilterBlocked(host: String, settings: BrowserSettings?): Boolean {
        return blockingService.isCustomFilterBlocked(host, settings)
    }

    fun isUrlRedirectingToBlocked(context: Context, url: String, settings: BrowserSettings?): Boolean {
        return redirectService.isUrlRedirectingToBlocked(context, url, settings)
    }

    fun isSearchEngineWithJudolQuery(context: Context, url: String): Boolean {
        return redirectService.isSearchEngineWithJudolQuery(context, url)
    }

    fun isDownloadFileUrl(url: String): Boolean {
        return redirectService.isDownloadFileUrl(url)
    }

    fun isOAuthOrLoginUrl(url: String, host: String): Boolean {
        return redirectService.isOAuthOrLoginUrl(url, host)
    }

    fun isUrlPathBlocked(url: String): Boolean {
        return redirectService.isUrlPathBlocked(url)
    }

    fun isThirdPartyRedirectBlocked(
        currentUrl: String?,
        targetUrl: String,
        targetHost: String,
        openerHost: String?,
        settings: BrowserSettings?,
        isAppNav: Boolean,
        hasGesture: Boolean,
        hitTestResult: WebView.HitTestResult?,
    ): Boolean {
        return redirectService.isThirdPartyRedirectBlocked(
            currentUrl, targetUrl, targetHost, openerHost,
            settings, isAppNav, hasGesture, hitTestResult,
        )
    }

    fun shouldBlockScriptPopupNavigation(
        openerHost: String?,
        targetHost: String,
        isScriptPopup: Boolean,
    ): Boolean {
        return redirectService.shouldBlockScriptPopupNavigation(openerHost, targetHost, isScriptPopup)
    }

    fun shouldBlockPopup(
        currentHost: String,
        isUserGesture: Boolean,
    ): Boolean {
        return redirectService.shouldBlockPopup(currentHost, isUserGesture)
    }

    // ========================================================================
    // Injection methods
    // ========================================================================

    fun injectCosmeticFilters(
        context: Context,
        view: WebView?,
        url: String?,
        settings: BrowserSettings? = null,
    ) {
        cosmeticService.injectCosmeticFilters(
            context = context,
            view = view,
            url = url,
            settings = settings,
            policy = activePolicy,
        )
    }

    fun injectYouTubeAdBlock(view: WebView?, url: String?) {
        youTubeService.injectAdBlock(view, url)
    }

    fun isYouTubeUrl(url: String?): Boolean {
        return youTubeService.isYouTubeUrl(url)
    }

    // ========================================================================
    // Cookie banner management
    // ========================================================================

    fun addCookieBannerSelector(selector: String) {
        cookieBannerRemover.addBannerSelector(selector)
    }

    fun addCookieBannerSelectors(selectors: Collection<String>) {
        cookieBannerRemover.addBannerSelectors(selectors)
    }

    fun addCookieBannerProviderDomain(domain: String, selector: String) {
        cookieBannerRemover.addProviderDomain(domain, selector)
    }

    fun getCookieBannerCSS(url: String): String {
        val host = try {
            android.net.Uri.parse(url).host?.lowercase(Locale.US) ?: return ""
        } catch (e: Exception) {
            return ""
        }
        val docContext = DocumentContext(url = url, host = host)
        return cookieBannerRemover.generateBannerCss(docContext, activePolicy?.cookieBanner?.maxRules ?: 200)
    }

    // ========================================================================
    // WebSocket blocking management
    // ========================================================================

    fun addBlockedWsHost(host: String) {
        webSocketBlocker.addBlockedWsHost(host)
    }

    fun addBlockedWsHosts(hosts: Collection<String>) {
        webSocketBlocker.addBlockedWsHosts(hosts)
    }

    fun addWsPattern(pattern: String) {
        webSocketBlocker.addWsPattern(pattern)
    }

    // ========================================================================
    // State management
    // ========================================================================

    fun getPolicyVersion(): Int {
        return activePolicy?.policyVersion ?: 0
    }

    fun getCurrentProfile(): String {
        return activePolicy?.profile ?: "balanced"
    }

    fun getActivePolicy(): AdBlockPolicy? = activePolicy

    fun syncNow(profile: String = lastProfile, onComplete: (() -> Unit)? = null) {
        lastProfile = profile
        scope.launch {
            val result = syncEngine.sync(profile)
            when (result) {
                is FilterSyncEngine.SyncResult.Success -> {
                    activePolicy = result.policy
                    loadLocalRules(getApplicationContext())
                    markSyncedNow()
                }
                else -> {}
            }
            onComplete?.invoke()
        }
    }

    fun setProfile(profile: String) {
        lastProfile = profile
        scope.launch {
            val result = syncEngine.sync(profile)
            when (result) {
                is FilterSyncEngine.SyncResult.Success -> {
                    activePolicy = result.policy
                    loadLocalRules(getApplicationContext())
                    markSyncedNow()
                }
                else -> {}
            }
        }
    }

    fun updateUserWhitelist(domains: Set<String>) {
        pipeline.setUserWhitelist(domains)
    }

    fun resetStats() {
        stats.reset()
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

    private val browserSettings: BrowserSettings?
        get() {
            return try {
                com.yue.browser.data.repository.SettingsRepositoryImpl.instance.settingsFlow.value
            } catch (e: Exception) {
                null
            }
        }

    private fun loadDefaultPolicy(): AdBlockPolicy {
        return AdBlockPolicy(
            policyVersion = 0,
            engineVersion = 1,
            profile = "balanced",
            network = NetworkPolicy(
                mode = com.yue.browser.data.adblock.config.BlockMode.BALANCED,
                thirdParty = com.yue.browser.data.adblock.config.ThirdPartyPolicy(
                    blockTrackers = true, blockAds = true, blockMalvertising = true,
                ),
                firstParty = com.yue.browser.data.adblock.config.FirstPartyPolicy(enabled = false),
                unknown = com.yue.browser.data.adblock.config.UnknownPolicy(
                    action = com.yue.browser.data.adblock.config.Action.ALLOW,
                ),
            ),
            navigation = NavigationPolicy(
                popup = com.yue.browser.data.adblock.config.PopupNavigationPolicy(
                    policy = com.yue.browser.data.adblock.config.PopupPolicy.SMART,
                ),
                redirect = com.yue.browser.data.adblock.config.RedirectPolicy(
                    maxChain = 3, thirdParty = com.yue.browser.data.adblock.config.Action.BLOCK,
                    sameSite = com.yue.browser.data.adblock.config.Action.ALLOW,
                    userGestureRequired = true, allowOAuth = true,
                ),
            ),
            cosmetic = com.yue.browser.data.adblock.config.CosmeticPolicy(
                enabled = true, strategy = "domain_aware",
                generic = com.yue.browser.data.adblock.config.GenericCosmeticConfig(maxRules = 500),
                iframe = com.yue.browser.data.adblock.config.IframeConfig(enabled = true),
            ),
            cookieBanner = CookieBannerPolicy(enabled = true, maxRules = 200),
            webSocket = WebSocketPolicy(blockTracking = true, blockAnalytics = true),
            scriptlet = com.yue.browser.data.adblock.config.ScriptletPolicy(enabled = true),
            youtube = com.yue.browser.data.adblock.config.YoutubePolicy(
                enabled = true, strategy = "adaptive",
                skipHosts = listOf("music.youtube.com"),
            ),
            antiAdblock = com.yue.browser.data.adblock.config.AntiAdblockPolicy(enabled = true),
            riskScoring = RiskScoringPolicy(
                blockThreshold = 80, warnThreshold = 50,
                signalScores = mapOf(
                    "BLOCKED_AD_DOMAIN" to 100,
                    "SUSPICIOUS_REDIRECT" to 40,
                    "JUDOL_KEYWORD" to 30,
                    "SUSPICIOUS_POPUP" to 20,
                    "TRACKER_FOUND" to 30,
                    "MALVERTISING_DOMAIN" to 80,
                    "WEBSOCKET_TRACKER" to 40,
                    "ANTI_ADBLOCK_DETECTED" to 50,
                ),
            ),
        )
    }

    private fun loadAssetRules(context: Context) {
        try {
            // Load ad domains from curated list
            val adDomains = readAssetLines(context, "filters/ad_domains.txt")
            hostBlocker.addBlockedHosts(adDomains)

            // Parse easylist.txt for domain-blocking patterns (||domain.com^)
            val easylistDomains = parseHostPatternsFromList(context, "adblock/easylist.txt")
            hostBlocker.addBlockedHosts(easylistDomains)

            // Parse abpindo.txt for domain-blocking patterns
            val abpindoDomains = parseHostPatternsFromList(context, "adblock/abpindo.txt")
            hostBlocker.addBlockedHosts(abpindoDomains)

            // Add hardcoded fallback domains
            hostBlocker.addBlockedHosts(listOf(
                "adsco.re", "adsco.re:2087",
                "doubleclick.net", "googlesyndication.com", "googleadservices.com",
                "google-analytics.com", "googletagmanager.com", "adsensecustomsearchads.com",
                "pagead2.googlesyndication.com", "pagead.googlesyndication.com",
                "adservice.google.com", "adservice.google.co.id",
                "amazon-adsystem.com", "ad.doubleclick.net", "pubads.g.doubleclick.net",
                "securepubads.g.doubleclick.net", "s0.2mdn.net", "ib.adnxs.com",
                "criteo.com", "criteo.net", "partner.criteo.com",
                "popads.net", "popcash.net", "propellerads.com", "mgid.com",
                "onclickads.net", "exoclick.com", "adsterra.com", "juicyads.com",
                "masonerthor.com", "ibo88.com", "ibo88.fun", "ibo88.net", "ibo88.org",
                "adsystem.com", "connect.facebook.net", "admob.com", "app-measurement.com",
                "googletagservices.com", "googlesynergy.com", "smartadserver.com",
                "openx.net", "rubiconproject.com", "adnxs.com", "advertising.com",
                "taboola.com", "outbrain.com", "content.ad", "zedo.com",
                "scorecardresearch.com", "quantserve.com", "chartbeat.com",
                "hotjar.com", "mouseflow.com", "luckyorange.com",
                "statcounter.com", "histats.com",
                "teads.tv", "teads.com", "aniview.com", "connatix.com",
                "primis.tech", "primis.tv", "vidoomy.com",
                "spotxchange.com", "tremorvideo.com", "brid.tv",
                "revcontent.com", "adblade.com", "innity.com",
                "gumgum.com", "undertone.com", "sovrn.com", "lijit.com",
                "infolinks.com", "buysellads.com", "media.net",
                "adcolony.com", "applovin.com", "ironsrc.com", "vungle.com",
                "chartboost.com", "adform.net",
            ))

            // Whitelist
            whitelistChecker.addWhitelistHosts(listOf(
                "html-load.com", "html-load.cc", "css-load.com",
                "content-loader.com", "img-load.com",
            ))

            // WebSocket tracking domains
            webSocketBlocker.addBlockedWsHosts(listOf(
                "analytics.google.com", "tracking.google.com",
                "analytics.facebook.com", "analytics.twitter.com",
                "hotjar.com", "mouseflow.com", "luckyorange.com",
                "mixpanel.com", "segment.io", "amplitude.com",
                "fullstory.com", "logrocket.com", "sentry.io",
            ))

            // Anti-adblock domains
            antiAdblockBypass.addAntiAdblockDomains(listOf(
                "blockadblock.com", "blockads.net", "fuckadblock.com",
                "adblockdetector.com", "detectadblock.com",
            ))

            // Load cosmetic selectors
            cosmeticEngine.addGenericSelectors(readAssetLines(context, "filters/default_generic_selectors.txt"))

            // Load keywords
            keywordDetector.addStrictKeywords(readAssetLines(context, "filters/gambling_strict.txt"))
            keywordDetector.addModerateKeywords(readAssetLines(context, "filters/gambling_moderate.txt"))

            Log.d(TAG, "Loaded asset rules: hosts=${hostBlocker.size()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load asset rules", e)
        }
    }

    private fun loadCookieBannerSelectors(context: Context) {
        try {
            val selectors = readAssetLines(context, "filters/cookie_banner_selectors.txt")
            cookieBannerRemover.addBannerSelectors(selectors)

            val cookieSelectors = listOf(
                "[data-cookiebanner]", "[data-cookie-banner]", "[data-gdpr]", "[data-consent]",
                "[class*='cookie-banner']", "[class*='cookie_banner']", "[class*='CookieBanner']",
                "[class*='gdpr-banner']", "[class*='consent-banner']", "[class*='cookie-notice']",
                "[class*='CookieNotice']",
                "#onetrust-banner-sdk", "#onetrust-consent-sdk", "#cookie-banner", "#cookieBanner",
                "#cookie_notice", "#cookieNotice",
                ".cookie-banner", ".CookieBanner", ".cookie-banner-wrapper", ".CookieBannerWrapper",
                ".gdpr-banner", ".consent-banner", ".cookie-notice", ".CookieNotice",
                ".banner-cookie", ".cookie-banner-v2", ".cookie-alert", ".cookie-popup", ".cookie-popup2",
                "[aria-label*='cookie' i]", "[aria-label*='consent' i]", "[aria-label*='GDPR' i]",
                "[aria-label*='privacy' i]",
                "[data-testid*='cookie' i]", "[data-testid*='consent' i]", "[data-testid*='gdpr' i]",
                "[role='dialog'][aria-label*='cookie' i]", "[role='dialog'][aria-label*='consent' i]",
                ".trustarc", ".truste", ".cookiebot", ".CookieBot",
                ".optanon-alert-box-wrapper", ".optanon-popup-wrapper",
                ".osano-cm-window", ".klaro-popup", ".klaro-notice",
                ".cookie-law-info-bar", "#cookie-law-info-extra-info",
                ".cc-window", ".cc-message", ".compliance-container", ".compliance-banner",
                "[class*='cookie-consent' i]", "[class*='cookieconsent' i]",
                "[id*='cookie-consent' i]", "[id*='cookieconsent' i]",
                ".cookie-consent-banner",
            )
            cookieBannerRemover.addBannerSelectors(cookieSelectors)

            Log.d(TAG, "Loaded cookie banner selectors: ${cookieBannerRemover.size()}")
        } catch (e: Exception) {
            Log.w(TAG, "Cookie banner selectors not found in assets", e)
        }
    }

    private fun loadGeneralAdSelectors() {
        val adSelectors = listOf(
            // Generic ad containers
            "[class*='ad-']", "[class*='ad_']", "[class*='ads-']", "[class*='ads_']",
            "[class*='Ad-']", "[class*='Ad_']", "[class*='ADS-']", "[class*='ADS_']",
            "[id*='ad-']", "[id*='ad_']", "[id*='ads-']", "[id*='ads_']",
            "[class*='advert']", "[class*='advertise']", "[class*='advertisement']",
            "[class*='promo']", "[class*='promotion']", "[class*='sponsored']",
            "[class*='Sponsored']", "[class*='recommend']", "[class*='recommendation']",
            "[class*='suggested']", "[class*='related-suggestion']",
            // Floating / popup / overlay ads
            "[class*='popup-ad']", "[class*='popupAd']", "[class*='popunder']",
            "[class*='popup-advert']", "[class*='overlay-ad']", "[class*='overlayAd']",
            "[class*='floating-ad']", "[class*='floatingAd']", "[class*='floating_ad']",
            "[class*='sticky-ad']", "[class*='stickyAd']", "[class*='sticky_ad']",
            "[class*='banner-ad']", "[class*='bannerAd']", "[class*='banner_ad']",
            "[class*='top-ad']", "[class*='topAd']", "[class*='bottom-ad']", "[class*='bottomAd']",
            "[class*='side-ad']", "[class*='sideAd']", "[class*='sidebar-ad']", "[class*='sidebarAd']",
            "[class*='interstitial']", "[class*='Interstitial']",
            "[class*='lightbox-ad']", "[class*='lightboxAd']",
            "[class*='modal-ad']", "[class*='modalAd']",
            "[class*='popin']", "[class*='pop-out']", "[class*='popout']",
            "[class*='exit-intent']", "[class*='exitIntent']",
            "[class*='entry-ad']", "[class*='entryAd']",
            // Iframe / script / tracking containers
            "[class*='ad-frame']", "[class*='adFrame']", "[class*='ad_frame']",
            "[class*='ads-frame']", "[class*='adsFrame']",
            "[id*='ad-frame']", "[id*='adFrame']", "[id*='ad_frame']",
            "[class*='google-ad']", "[class*='googleAd']",
            "[class*='doubleclick']", "[class*='DoubleClick']",
            "[class*='adsense']", "[class*='AdSense']", "[class*='Adsense']",
            "[class*='adserver']", "[class*='AdServer']",
            "[class*='adtech']", "[class*='AdTech']",
            "[class*='taboola']", "[class*='outbrain']", "[class*='mgid']",
            "[class*='contentad']", "[class*='revcontent']",
            "[class*='zemanta']", "[class*='zemantahub']",
            // Common ad platform classes
            ".ad-slot", ".adSlot", ".ad_slot", ".ads-slot", ".adsSlot",
            ".ad-container", ".adContainer", ".ad_container",
            ".advertisement", ".advertisement-wrapper",
            ".ad-wrapper", ".adWrapper", ".ad_wrapper",
            ".ads-wrapper", ".adsWrapper", ".ads_wrapper",
            ".promoted-content", ".promotedContent",
            ".sponsored-content", ".sponsoredContent",
            ".recommendations", ".recommendation",
            // Data attributes commonly used by ad platforms
            "[data-ad]", "[data-ad-slot]", "[data-ad-sizes]", "[data-ad-client]",
            "[data-ad-format]", "[data-ad-layout]",
            "[data-google-query-id]", "[data-google-ad-client]",
            "[data-ad-provider]", "[data-ad-network]",
            "[data-sponsored]", "[data-promoted]",
            "[data-advertisement]", "[data-advertiser]",
            "[data-creative]", "[data-creative-id]",
            "[data-line-item-id]", "[data-gpt-slot]",
            "[data-gpt-ad-unit-path]", "[data-gpt-size]",
            // A10 / ADB / data-ad-platform attributes
            "[data-ad-platform]", "[data-ad-platform-client]",
            "[data-ad-server]", "[data-ad-type]",
            "[data-ad-slot-name]", "[data-ad-slot-id]",
            "[data-ad-refresh]", "[data-ad-position]",
            "[data-ad-zone]", "[data-ad-format]",
            "[data-ad-height]", "[data-ad-width]",
            "[data-ad-priority]", "[data-ad-name]",
            "[data-ad-divider]", "[data-ad-label]",
            "[data-nad-slot]", "[data-nad-client]",
            // General ad related data attributes
            "[data-sponsored=\"true\"]", "[data-ad=\"true\"]",
            "[data-type=\"ad\"]", "[data-type=\"advertisement\"]",
            // ARIA / accessibility patterns
            "[role='region'][aria-label*='ad' i]",
            "[role='region'][aria-label*='Ad' i]",
            "[aria-label*='advertisement' i]",
            "[aria-label*='sponsored' i]",
            "[aria-label*='promoted' i]",
            "[aria-label*='ad unit' i]",
            "[aria-label*='ad slot' i]",
            // YouTube / video specific
            "[class*='video-ad']", "[class*='videoAd']",
            "[class*='pre-roll']", "[class*='preroll']",
            "[class*='mid-roll']", "[class*='midroll']",
            "[class*='post-roll']", "[class*='postroll']",
            // Common ad provider containers
            "[id*='div-gpt-ad']", "[class*='div-gpt-ad']",
            "[id*='google_translate']",
            "[class*='adsbygoogle']", "[class*='adsby']",
            "[class*='mgid-']", "[class*='mgid_']",
            "[class*='revcontent-']", "[class*='revcontent_']",
            "[class*='outbrain-']", "[class*='outbrain_']",
            "[class*='taboola-']", "[class*='taboola_']",
            // Gambling / betting ads
            "[class*='betting']", "[class*='betting-']",
            "[class*='casino']", "[class*='casino-']",
            "[class*='gambling']", "[class*='gambling-']",
            "[class*='slot-']", "[class*='slot_']",
        )
        cookieBannerRemover.addGeneralAdSelectors(adSelectors)

        Log.d(TAG, "Loaded general ad selectors: ${cookieBannerRemover.size()}")
    }

    private fun loadLocalRules(context: Context) {
        val rulesDir = File(context.filesDir, "adblock/rules")
        if (!rulesDir.exists()) return

        try {
            hostBlocker.clearHosts()

            val adDomainsFile = File(rulesDir, "ad_domains.txt")
            if (adDomainsFile.exists()) {
                val domains = adDomainsFile.readLines()
                    .map { it.trim().lowercase(Locale.US) }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                hostBlocker.addBlockedHosts(domains)
            }

            val easylistDomains = parseHostPatternsFromFile(File(rulesDir, "easylist.txt"))
            hostBlocker.addBlockedHosts(easylistDomains)

            val abpindoDomains = parseHostPatternsFromFile(File(rulesDir, "abpindo.txt"))
            hostBlocker.addBlockedHosts(abpindoDomains)

            val whitelistFile = File(rulesDir, "whitelist.txt")
            if (whitelistFile.exists()) {
                hostBlocker.clearWhitelist()
                val hosts = whitelistFile.readLines()
                    .map { it.trim().lowercase(Locale.US) }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                whitelistChecker.addWhitelistHosts(hosts)
            }

            val cosmeticFile = File(rulesDir, "cosmetic_selectors.txt")
            if (cosmeticFile.exists()) {
                cosmeticEngine.addGenericSelectors(
                    cosmeticFile.readLines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                )
            }

            val cookieBannerFile = File(rulesDir, "cookie_banner_selectors.txt")
            if (cookieBannerFile.exists()) {
                cookieBannerRemover.addBannerSelectors(
                    cookieBannerFile.readLines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                )
            }

            Log.d(TAG, "Loaded local rules: hosts=${hostBlocker.size()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load local rules", e)
        }
    }

    private fun parseHostPatternsFromFile(file: File): List<String> {
        if (!file.exists()) return emptyList()
        return try {
            val hostSet = mutableSetOf<String>()
            file.useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("!")) return@forEach
                    val raw = trimmed.substringBefore('$')
                    when {
                        raw.startsWith("||") && raw.endsWith("^") -> {
                            val domain = raw.removePrefix("||").removeSuffix("^")
                            if (domain.isNotEmpty() && !domain.contains("/")) {
                                hostSet.add(domain)
                            }
                        }
                        raw.startsWith("|https://") || raw.startsWith("|http://") -> {
                            val url = raw.removePrefix("|")
                            val host = try {
                                android.net.Uri.parse(url).host
                            } catch (_: Exception) {
                                null
                            }
                            if (host != null) {
                                hostSet.add(host)
                            }
                        }
                        !raw.startsWith("||") && !raw.startsWith("|") && !raw.contains("##") && !raw.contains("@@") -> {
                            if (raw.contains("###") || raw.contains("##")) return@forEach
                            val withoutPipe = raw.removePrefix("||").removePrefix("|")
                            val domain = withoutPipe.substringBefore("/").substringBefore("^")
                            if (domain.isNotEmpty() && domain.contains(".") && !domain.contains("*") && !domain.contains("?")) {
                                hostSet.add(domain)
                            }
                        }
                    }
                }
            }
            hostSet.toList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse host patterns from ${file.name}: ${e.message}")
            emptyList()
        }
    }

    private fun readAssetLines(context: Context, path: String): List<String> {
        return try {
            context.assets.open(path)
                .bufferedReader()
                .readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Parse domain-blocking patterns from adblock filter lists.
     * Matches patterns like: ||domain.com^ or |https://domain.com/
     * Returns extracted hostnames.
     */
    private fun parseHostPatternsFromList(context: Context, path: String): List<String> {
        return try {
            val hostSet = mutableSetOf<String>()
            context.assets.open(path).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("!")) return@forEach
                    val raw = trimmed.substringBefore('$')
                    when {
                        // Pattern: ||domain.com^
                        raw.startsWith("||") && raw.endsWith("^") -> {
                            val domain = raw.removePrefix("||").removeSuffix("^")
                            if (domain.isNotEmpty() && !domain.contains("/")) {
                                hostSet.add(domain)
                            }
                        }
                        // Pattern: |https://domain.com/
                        raw.startsWith("|https://") || raw.startsWith("|http://") -> {
                            val url = raw.removePrefix("|")
                            val host = try {
                                android.net.Uri.parse(url).host
                            } catch (_: Exception) {
                                null
                            }
                            if (host != null) {
                                hostSet.add(host)
                            }
                        }
                        // Wildcard: domain.com* or domain.com/
                        !raw.startsWith("||") && !raw.startsWith("|") && !raw.contains("##") && !raw.contains("@@") -> {
                            // Skip cosmetic selector lines and options
                            if (raw.contains("###") || raw.contains("##")) return@forEach
                            val withoutPipe = raw.removePrefix("||").removePrefix("|")
                            val domain = withoutPipe.substringBefore("/").substringBefore("^")
                            if (domain.isNotEmpty() && domain.contains(".") && !domain.contains("*") && !domain.contains("?")) {
                                hostSet.add(domain)
                            }
                        }
                    }
                }
            }
            Log.d(TAG, "Parsed ${hostSet.size} host patterns from $path")
            hostSet.toList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse host patterns from $path: ${e.message}")
            emptyList()
        }
    }
}
