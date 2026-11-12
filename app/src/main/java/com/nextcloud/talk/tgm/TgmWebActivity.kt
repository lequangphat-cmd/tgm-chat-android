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
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
    private var selectedTab by mutableStateOf(TgmTab.CONTACTS)
    private var loading by mutableStateOf(true)
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
                            factory = { context -> createWebView(context).also { webView = it } }
                        )
                        if (loading) {
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
        selectedTab = tab
        webView?.loadUrl(baseUrl + tab.path, mapOf("Authorization" to authHeader))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView {
        CookieManager.getInstance().setAcceptCookie(true)
        val view = WebView(context)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.userAgentString = view.settings.userAgentString + " TGMChatApp"
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)

        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                handleLink(request.url)

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                loading = true
                view.evaluateJavascript(HIDE_WEB_SHELL_JS, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                loading = false
                view.evaluateJavascript(HIDE_WEB_SHELL_JS, null)
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
        view.loadUrl(baseUrl + (selectedTab.path ?: "/"), mapOf("Authorization" to authHeader))
        return view
    }

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
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    override val appBarLayoutType: AppBarLayoutType
        get() = AppBarLayoutType.EMPTY

    companion object {
        private const val EXTRA_TAB = "tgm_tab"
        private const val EXTRA_BASE_URL = "tgm_base_url"
        private const val EXTRA_USERNAME = "tgm_username"
        private const val EXTRA_TOKEN = "tgm_token"

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
                'body { padding-left: 0 !important; padding-bottom: 0 !important; }';
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
