/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.jelly.webview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.webkit.WebView
import androidx.constraintlayout.widget.ConstraintLayout
import com.familymdm.browser.Guard
import org.lineageos.jelly.R
import org.lineageos.jelly.js.JsManifest
import org.lineageos.jelly.js.JsMediaSession
import org.lineageos.jelly.js.JsShare
import org.lineageos.jelly.js.JsSyncUrl
import org.lineageos.jelly.shortcut.BackgroundShortcut
import org.lineageos.jelly.shortcut.BackgroundShortcutService
import org.lineageos.jelly.ui.UrlBarLayout
import org.lineageos.jelly.utils.SharedPreferencesExt
import org.lineageos.jelly.utils.UrlUtils
import java.util.regex.Pattern

class WebViewExt @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    backgroundShortcut: BackgroundShortcut? = null,
    backgroundShortcutService: BackgroundShortcutService? = null
) : WebView(context, attrs, defStyle), com.familymdm.browser.PageHost {
    /** The address one of our pages (not allowed, ask...) stands for: shown in the address bar. */
    @Volatile
    var shownFor: String? = null

    override fun showingPageFor(url: String?) {
        shownFor = url
    }

    private lateinit var activity: WebViewExtActivity
    val requestHeaders = mutableMapOf<String?, String?>()
    private var mobileUserAgent: String? = null
    private var desktopUserAgent: String? = null
    var isIncognito = false
        private set
    private var desktopMode = false
    var lastLoadedUrl: String? = null
        private set

    private var adScriptHandler: androidx.webkit.ScriptHandler? = null
    private var adScriptOn = false

    /** "Block ads": YouTube's ad remover runs before YouTube's own scripts (where the phone supports it). */
    fun syncAdBlock() {
        val js = Guard.adBlockScript(context)
        if ((js != null) == adScriptOn) return
        adScriptHandler?.remove()
        adScriptHandler = null
        adScriptOn = js != null
        if (js != null && androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
            adScriptHandler = androidx.webkit.WebViewCompat.addDocumentStartJavaScript(this, js,
                setOf("https://www.youtube.com", "https://m.youtube.com", "https://youtube.com", "https://music.youtube.com"))
        }
    }

    /** The site of the page shown (for "ads load but are covered"). */
    @Volatile
    var pageHost: String? = null

    /** The watcher (adblock_detect.js) says this page will not open with ads blocked. */
    inner class AdsBridge {
        @android.webkit.JavascriptInterface
        fun blocked(host: String?) {
            if (Guard.noticedAdBlockWall(context, host, pageHost)) post {
                android.widget.Toast.makeText(context, "This site will not open with ads blocked: its ads now load, covered in black.", android.widget.Toast.LENGTH_LONG).show()
                reload()
            }
        }
    }

    /** The page is an allowed YouTube channel or video: its pictures and videos show as they are. */
    @Volatile
    var trustedPage = false
    var backgroundShortcut = backgroundShortcut
        private set
    var backgroundShortcutService = backgroundShortcutService
        private set
    var initialized: Boolean = false
        private set

    private val sharedPreferencesExt by lazy { SharedPreferencesExt(context) }

    override fun loadUrl(url: String) {
        lastLoadedUrl = url
        followUrl(url)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        val backgroundMode = backgroundShortcutService != null
        super.onWindowVisibilityChanged(
            if (backgroundMode) View.VISIBLE else visibility
        )
    }

    /** Every address goes through the family dashboard's rules (Guard) first. */
    fun followUrl(url: String) {
        if (Guard.handleAction(context, this, url)) return
        val target = UrlUtils.smartUrlFilter(url) ?: Guard.searchUrl(context, url)
        if (target == null) {
            Guard.showPage(this, null, Guard.noSearchPage())
            return
        }
        openChecked(Guard.safeUrl(context, target))
    }

    /** Opens `url` if the rules allow it, otherwise shows why not (or the start page for the home page). */
    fun openChecked(url: String) {
        if (Guard.useStartPage(context, url, sharedPreferencesExt.homePage)) {
            Guard.showPage(this, null, Guard.startPage(context))
            return
        }
        val decision = Guard.check(context, url) { post { openChecked(url) } }
        if (!decision.allow) {
            Guard.showPage(this, url, decision.html)
            return
        }
        settings.blockNetworkImage = decision.blockImages
        settings.loadsImagesAutomatically = !decision.blockImages
        trustedPage = decision.trusted
        pageHost = android.net.Uri.parse(url).host
        super.loadUrl(url, this.requestHeaders)
    }

    private fun setup(urlBarLayout: UrlBarLayout) {
        settings.javaScriptEnabled = sharedPreferencesExt.javascriptEnabled
        settings.javaScriptCanOpenWindowsAutomatically = sharedPreferencesExt.javascriptEnabled
        settings.setGeolocationEnabled(sharedPreferencesExt.locationEnabled)
        settings.setSupportMultipleWindows(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.domStorageEnabled = !isIncognito
        setOnLongClickListener(object : OnLongClickListener {
            override fun onLongClick(v: View): Boolean {
                val result = hitTestResult
                result.extra?.let {
                    when (result.type) {
                        HitTestResult.IMAGE_TYPE, HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                            activity.showSheetMenu(it, true)
                            return true
                        }

                        HitTestResult.SRC_ANCHOR_TYPE -> {
                            activity.showSheetMenu(it, false)
                            return true
                        }

                        else -> {
                            return false
                        }
                    }
                }
                return false
            }
        })
        setDownloadListener { url: String?, userAgent: String?, contentDisposition: String?,
                              mimeType: String?, contentLength: Long ->
            activity.downloadFileAsk(url, userAgent, contentDisposition, mimeType, contentLength)
        }

        // Mobile: Remove "wv" and "Version/4.0" from the WebView's user agent.
        // Some websites don't work properly if the browser reports itself as a simple WebView.
        // Desktop: Generate the desktop user agent starting from the mobile one so that
        // we always report the current engine version.
        val pattern = Pattern.compile("([^)]+ \\()([^)]+)(\\) .*)")
        val matcher = pattern.matcher(settings.userAgentString)
        if (matcher.matches()) {
            val mobileDevice = matcher.group(2)!!.replace("; wv", "")
            mobileUserAgent = matcher.group(1)!! + mobileDevice + matcher.group(3)!!
                .replace(" Version/4.0 ", " ")
            desktopUserAgent = matcher.group(1)!! + DESKTOP_DEVICE + matcher.group(3)!!
                .replace(" Mobile ", " ")
                .replace(" Version/4.0 ", " ")
            settings.userAgentString = mobileUserAgent
        } else {
            Log.e(TAG, "Couldn't parse the user agent")
            mobileUserAgent = settings.userAgentString
            desktopUserAgent = DESKTOP_USER_AGENT_FALLBACK
        }
        if (sharedPreferencesExt.doNotTrackEnabled) {
            this.requestHeaders[HEADER_DNT] = "1"
        }

        addJavascriptInterface(AdsBridge(), "MdmAds")
        if (settings.javaScriptEnabled) {
            addJavascriptInterface(
                JsSyncUrl(urlBarLayout, activity),
                JsSyncUrl.INTERFACE
            )
            addJavascriptInterface(
                JsManifest(activity),
                JsManifest.INTERFACE
            )
            addJavascriptInterface(
                JsMediaSession(this),
                JsMediaSession.INTERFACE
            )
            addJavascriptInterface(
                JsShare(activity),
                JsShare.INTERFACE
            )
        }
    }

    fun init(
        activity: WebViewExtActivity,
        urlBarLayout: UrlBarLayout,
        desktopMode: Boolean,
        incognito: Boolean,
    ) {
        if (initialized) return
        this.activity = activity
        isIncognito = incognito
        val chromeClient = ChromeClient(
            activity, incognito, urlBarLayout, sharedPreferencesExt
        )
        webChromeClient = chromeClient
        webViewClient = WebClient(activity, urlBarLayout)
        setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            urlBarLayout.searchPositionInfo = Pair(activeMatchOrdinal, numberOfMatches)
        }
        urlBarLayout.onLoadUrlCallback = { loadUrl(it) }
        urlBarLayout.onStartSearchCallback = { findAllAsync(it) }
        urlBarLayout.onClearSearchCallback = { clearMatches() }
        urlBarLayout.onSearchPositionChangeCallback = { findNext(it) }
        setup(urlBarLayout)
        setWebViewDesktopMode(desktopMode)
        initialized = true
    }

    val snap: Bitmap
        get() {
            measure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            layout(0, 0, measuredWidth, measuredHeight)
            val size = if (measuredWidth > measuredHeight) measuredHeight else measuredWidth
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint()
            val height = bitmap.height
            canvas.drawBitmap(bitmap, 0f, height.toFloat(), paint)
            draw(canvas)
            return bitmap
        }

    var isDesktopMode: Boolean
        get() = desktopMode
        set(value) {
            setWebViewDesktopMode(value)
            reload()
        }

    private fun setWebViewDesktopMode(value: Boolean) {
        desktopMode = value
        settings.apply {
            userAgentString = if (value) desktopUserAgent else mobileUserAgent
            useWideViewPort = value
            loadWithOverviewMode = value
        }
    }

    companion object {
        private const val TAG = "WebViewExt"
        private const val DESKTOP_DEVICE = "X11; Linux x86_64"
        private const val DESKTOP_USER_AGENT_FALLBACK =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val HEADER_DNT = "DNT"

        fun newInstance(
            context: Context,
            backgroundShortcut: BackgroundShortcut? = null,
            backgroundShortcutService: BackgroundShortcutService? = null
        ) = WebViewExt(
            context,
            backgroundShortcut = backgroundShortcut,
            backgroundShortcutService = backgroundShortcutService
        ).apply {
            id = R.id.webView
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = ConstraintLayout.LayoutParams(0, 0).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                topToBottom = R.id.appBarLayout
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            }
        }
    }
}
