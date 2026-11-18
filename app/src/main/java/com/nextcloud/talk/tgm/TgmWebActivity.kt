/*
 * TGM Chat - Android Client
 *
 * SPDX-FileCopyrightText: 2026 WAM
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.tgm

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN

/**
 * TGM: shows a TGM Chat web page (Danh bạ, Lịch, Tệp tin, Nhiệm vụ) inside the app, signed in
 * with the account's app password, under the same bottom navigation as the chat list.
 */
@AutoInjector(NextcloudTalkApplication::class)
class TgmWebActivity : BaseActivity() {

    private var webView: WebView? = null
    private var container: FrameLayout? = null
    private var selectedTab by mutableStateOf(TgmTab.CONTACTS)
    private var loading by mutableStateOf(true)
    private var firstLoad by mutableStateOf(true)
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val baseUrl by lazy { intent.getStringExtra(EXTRA_BASE_URL).orEmpty().trimEnd('/') }
    private val authHeader by lazy {
        val raw = "${intent.getStringExtra(EXTRA_USERNAME)}:${intent.getStringExtra(EXTRA_TOKEN)}"
        "Basic " + Base64.encodeToString(raw.toByteArray(), Base64.NO_WRAP)
    }

    private val pickFiles = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        fileCallback?.onReceiveValue(uris.toTypedArray())
        fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        selectedTab = TgmTab.valueOf(intent.getStringExtra(EXTRA_TAB) ?: TgmTab.CONTACTS.name)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val view = webView
                    if (view != null && view.canGoBack()) view.goBack() else finish()
                }
            }
        )

        setContent {
            MaterialTheme(colorScheme = viewThemeUtils.getColorScheme(this)) {
                ColoredStatusBar()
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        TgmBottomNav(selected = selectedTab, user = null) { tab -> selectTab(tab) }
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                FrameLayout(context).also {
                                    container = it
                                    showTab(selectedTab)
                                }
                            }
                        )
                        if (firstLoad) {
                            // cover the blank page while Nextcloud starts the first time
                            Box(
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        } else if (loading) {
                            LinearProgressIndicator(modifier = Modifier.align(Alignment.TopCenter))
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_TAB)?.let { selectTab(TgmTab.valueOf(it)) }
    }

    private fun selectTab(tab: TgmTab) {
        if (tab.path == null) {
            finish()
            return
        }
        if (tab == selectedTab && webView != null) {
            // same tab tapped again: back to the start page of the tab
            webView?.let { load(it, baseUrl + tab.path) }
            return
        }
        selectedTab = tab
        showTab(tab)
    }

    /**
     * TGM: each web tab keeps its own WebView in [TgmWebPool], so switching tabs (or going back to
     * Tin nhắn and returning) shows the page at once instead of loading Nextcloud again.
     */
    private fun showTab(tab: TgmTab) {
        val host = container ?: return
        val key = poolKey(tab)
        val (view, isNew) = TgmWebPool.obtain(this, key) { context -> newWebView(context) }
        attachClients(view)
        host.removeAllViews()
        (view.parent as? FrameLayout)?.removeView(view)
        val match = FrameLayout.LayoutParams.MATCH_PARENT
        host.addView(view, FrameLayout.LayoutParams(match, match))
        webView = view
        firstLoad = isNew || view.progress < FULL_PROGRESS
        if (isNew) {
            loading = true
            load(view, baseUrl + (tab.path ?: "/"))
        } else {
            loading = view.progress < FULL_PROGRESS
        }
    }

    /** Loads the other web tabs in the background once the first one is ready. */
    private fun preloadOtherTabs() {
        TgmTab.visible.filter { it.path != null && it != selectedTab }.forEach { tab ->
            val (view, isNew) = TgmWebPool.obtain(this, poolKey(tab)) { context -> newWebView(context) }
            if (isNew) {
                attachClients(view)
                load(view, baseUrl + tab.path)
            }
        }
    }

    /**
     * TGM: only the first page logs in with the app password. Nextcloud starts a new session on
     * every basic-auth login, so sending the header from several tabs at once logged the others out
     * (stuck "Đang tải danh bạ", Tệp tin showing the login page). Later pages share the cookie.
     */
    private fun load(view: WebView, url: String) {
        if (TgmWebPool.sessionReady) {
            view.loadUrl(url)
        } else {
            view.loadUrl(url, mapOf("Authorization" to authHeader))
        }
    }

    private fun poolKey(tab: TgmTab) = "$baseUrl|${intent.getStringExtra(EXTRA_USERNAME)}|${tab.name}"

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(context: Context): WebView {
        CookieManager.getInstance().setAcceptCookie(true)
        val view = WebView(context)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.offscreenPreRaster = true
        view.settings.userAgentString = view.settings.userAgentString + " TGMChatApp"
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        // hide the web menu before the first paint, so the web bottom bar never flashes over the app's
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(view, HIDE_WEB_SHELL_JS, setOf("*"))
            WebViewCompat.addDocumentStartJavaScript(view, SKIP_BROWSER_CHECK_JS, setOf("*"))
        }
        return view
    }

    /** Clients point at the current activity, so they are set again whenever a pooled view is shown. */
    private fun attachClients(view: WebView) {
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                handleLink(request.url)

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (view === webView) loading = true
                view.evaluateJavascript(HIDE_WEB_SHELL_JS, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(HIDE_WEB_SHELL_JS, null)
                val page = url?.toUri()
                val onLoginPage = page?.path?.removePrefix("/index.php")?.startsWith("/login") == true
                if (onLoginPage && view.tag != LOGIN_RETRIED) {
                    // the session ran out: log in again with the app password, once
                    view.tag = LOGIN_RETRIED
                    TgmWebPool.sessionReady = false
                    load(view, baseUrl + (page?.getQueryParameter("redirect_url") ?: "/"))
                    return
                }
                if (!onLoginPage && url?.startsWith(baseUrl) == true) {
                    view.tag = null
                    TgmWebPool.sessionReady = true
                }
                if (view === webView) {
                    loading = false
                    firstLoad = false
                    if (!preloaded && TgmWebPool.sessionReady) {
                        preloaded = true
                        preloadOtherTabs()
                    }
                }
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                pickFiles.launch("*/*")
                return true
            }
        }
        view.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            download(url, userAgent, contentDisposition, mimetype)
        }
    }

    private var preloaded = false

    /** Chat and call links open the native chat, other servers open in the browser. */
    private fun handleLink(uri: Uri): Boolean {
        val url = uri.toString()
        if (!url.startsWith(baseUrl)) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: ActivityNotFoundException) {
                // nothing can open it
            }
            return true
        }
        val path = url.removePrefix(baseUrl).removePrefix("/index.php")
        val token = CALL_LINK.find(path)?.groupValues?.get(1)
        if (token != null) {
            startActivity(Intent(this, ChatActivity::class.java).putExtra(KEY_ROOM_TOKEN, token))
            return true
        }
        if (path.startsWith("/apps/spreed")) {
            finish()
            return true
        }
        return false
    }

    private fun download(url: String, userAgent: String, contentDisposition: String?, mimetype: String?) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
        val request = DownloadManager.Request(url.toUri())
            .addRequestHeader("Authorization", authHeader)
            .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url).orEmpty())
            .addRequestHeader("User-Agent", userAgent)
            .setMimeType(mimetype)
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        Toast.makeText(this, getString(R.string.tgm_downloading, fileName), Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        // keep the pooled WebViews alive, only let go of this activity
        container?.removeAllViews()
        container = null
        webView = null
        TgmWebPool.release(this)
        super.onDestroy()
    }

    override val appBarLayoutType: AppBarLayoutType
        get() = AppBarLayoutType.EMPTY

    companion object {
        private const val EXTRA_TAB = "tgm_tab"
        private const val EXTRA_BASE_URL = "tgm_base_url"
        private const val EXTRA_USERNAME = "tgm_username"
        private const val EXTRA_TOKEN = "tgm_token"

        private const val FULL_PROGRESS = 100

        private const val LOGIN_RETRIED = "tgm-login-retried"

        /**
         * Nextcloud sends older Android WebViews to an "unsupported browser" page (seen on Lịch).
         * The app's WebView is good enough, so store the "continue anyway" choice before the page runs.
         */
        private const val SKIP_BROWSER_CHECK_JS = """
            try {
              // @nextcloud/browser-storage key: persistent scope "core" (base64), see
              // core/src/utils/RedirectUnsupportedBrowsers.js
              localStorage.setItem('nextcloud_per_' + btoa('core') + '_unsupported-browser-ignore', 'true');
            } catch (e) {}
        """

        private val CALL_LINK = Regex("""^/call/([A-Za-z0-9]+)""")

        /** Hides the web menu, header and bottom bar: the app draws its own navigation. */
        private const val HIDE_WEB_SHELL_JS = """
            (function () {
              if (document.getElementById('tgm-app-embed')) return;
              var style = document.createElement('style');
              style.id = 'tgm-app-embed';
              style.textContent =
                '.tgm-sidebar, .tgm-bottom-nav, #header { display: none !important; }' +
                ':root, body { --header-height: 0px !important; --tgm-sidebar-width: 0px !important;' +
                ' --tgm-bottom-nav-height: 0px !important; }' +
                'body { padding-left: 0 !important; padding-bottom: 0 !important; }' +
                '#content.app-calendar .app-content { padding-top: 50px !important;' +
                ' box-sizing: border-box !important; }';
              (document.head || document.documentElement).appendChild(style);
            })();
        """

        fun open(context: Context, user: User, tab: TgmTab) {
            val intent = Intent(context, TgmWebActivity::class.java)
                .putExtra(EXTRA_TAB, tab.name)
                .putExtra(EXTRA_BASE_URL, user.baseUrl.orEmpty())
                .putExtra(EXTRA_USERNAME, user.username)
                .putExtra(EXTRA_TOKEN, user.token)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            context.startActivity(intent)
        }
    }
}

/** TGM: keeps one WebView per web tab for the life of the app process. */
object TgmWebPool {
    private val views = HashMap<String, WebView>()

    /** True once a web page loaded with a logged-in session cookie. */
    var sessionReady = false

    /** Returns the WebView for [key] (bound to [activity]) and whether it was just created. */
    fun obtain(activity: Context, key: String, create: (Context) -> WebView): Pair<WebView, Boolean> {
        views[key]?.let { view ->
            (view.context as? MutableContextWrapper)?.baseContext = activity
            return view to false
        }
        val view = create(MutableContextWrapper(activity))
        views[key] = view
        return view to true
    }

    /** Points the pooled views at the application context so [activity] can be freed. */
    fun release(activity: Context) {
        views.values.forEach { view ->
            val wrapper = view.context as? MutableContextWrapper
            if (wrapper?.baseContext === activity) wrapper.baseContext = activity.applicationContext
        }
    }

    /** Drops every pooled view, e.g. after logout. */
    fun clear() {
        views.values.forEach { it.destroy() }
        views.clear()
        sessionReady = false
    }
}
