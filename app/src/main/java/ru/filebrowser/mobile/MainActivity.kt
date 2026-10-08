package ru.filebrowser.mobile

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var errorContainer: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var toolbar: Toolbar

    private lateinit var urlStore: ServerUrlStore

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback ?: return@registerForActivityResult
        filePathCallback = null

        if (result.resultCode != RESULT_OK) {
            callback.onReceiveValue(null)
            return@registerForActivityResult
        }

        val data = result.data
        val uris: Array<Uri>? = when {
            data == null -> null
            data.clipData != null -> {
                val clip = data.clipData!!
                Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
            }
            data.data != null -> arrayOf(data.data!!)
            else -> null
        }
        callback.onReceiveValue(uris)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlStore = ServerUrlStore(this)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        errorContainer = findViewById(R.id.errorContainer)
        errorText = findViewById(R.id.errorText)
        webView = findViewById(R.id.webView)

        findViewById<MaterialButton>(R.id.retryButton).setOnClickListener {
            val url = urlStore.get() ?: return@setOnClickListener showServerDialog()
            loadUrl(url)
        }
        findViewById<MaterialButton>(R.id.changeServerButton).setOnClickListener {
            showServerDialog()
        }

        swipeRefresh.setOnRefreshListener { webView.reload() }
        swipeRefresh.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }

        setupWebView()
        setupBackHandler()

        val saved = urlStore.get()
        if (saved.isNullOrBlank()) {
            showServerDialog()
        } else {
            loadUrl(saved)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return handleUri(request.url)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (errorContainer.isVisible) {
                    errorContainer.isVisible = false
                    webView.isVisible = true
                }
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                super.onPageCommitVisible(view, url)
                checkForWebViewErrorPage(view)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                progressBar.isVisible = false
                swipeRefresh.isRefreshing = false
                CookieManager.getInstance().flush()
                checkForWebViewErrorPage(view)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    view.stopLoading()
                    showError(mapError(error.errorCode))
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (!request.isForMainFrame) return
                when (errorResponse.statusCode) {
                    401 -> showError(getString(R.string.http_401))
                    403 -> showError(getString(R.string.http_403))
                    404 -> showError(getString(R.string.http_404))
                    500 -> showError(getString(R.string.http_500))
                    502 -> showError(getString(R.string.http_502))
                    503 -> showError(getString(R.string.http_503))
                    504 -> showError(getString(R.string.http_504))
                    else -> Unit
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                handler.cancel()
                view.stopLoading()
                showError(getString(R.string.error_ssl_certificate))
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.isVisible = true
                    progressBar.progress = newProgress
                } else {
                    progressBar.isVisible = false
                }
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams.createIntent().apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    if (fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                }
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (_: ActivityNotFoundException) {
                    this@MainActivity.filePathCallback = null
                    filePathCallback.onReceiveValue(null)
                    false
                }
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            handleDownload(url, userAgent, contentDisposition, mimetype)
        }
    }

    private fun checkForWebViewErrorPage(view: WebView) {
        view.evaluateJavascript(
            "(function(){return document.title || '';})();"
        ) { raw ->
            val title = raw?.trim('"') ?: ""
            val isError =
                title.contains("net::ERR", ignoreCase = true) ||
                        title.contains("Не удалось открыть", ignoreCase = true) ||
                        title.contains("Webpage not available", ignoreCase = true) ||
                        title.contains("ERR_", ignoreCase = true) ||
                        title.contains("This site can", ignoreCase = true)

            if (isError) {
                view.stopLoading()
                view.loadUrl("about:blank")
                showError(getString(R.string.error_webview_unavailable))
            } else if (!errorContainer.isVisible) {
                webView.isVisible = true
            }
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack() && !errorContainer.isVisible) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun handleUri(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        return when (scheme) {
            "http", "https" -> {
                val currentHost = urlStore.get()?.toUri()?.host
                val uriHost = uri.host
                if (currentHost != null && uriHost != null && uriHost == currentHost) {
                    false
                } else {
                    openExternal(uri)
                    true
                }
            }
            "mailto", "tel", "sms", "intent" -> {
                openExternal(uri)
                true
            }
            else -> {
                openExternal(uri)
                true
            }
        }
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.no_app_for_link), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.could_not_open_link), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimetype: String
    ) {
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
            val request = DownloadManager.Request(url.toUri()).apply {
                setMimeType(mimetype)
                setTitle(fileName)
                setDescription(getString(R.string.download_description))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                CookieManager.getInstance().getCookie(url)?.let {
                    addRequestHeader("Cookie", it)
                }
                userAgent.takeIf { it.isNotBlank() }?.let {
                    addRequestHeader("User-Agent", it)
                }
            }
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, getString(R.string.download_started), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.download_failed) + ": ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun loadUrl(url: String) {
        errorContainer.isVisible = false
        webView.isVisible = true
        progressBar.isVisible = true
        progressBar.progress = 0

        lifecycleScope.launch {
            val error = ServerCheck.check(url)
            if (error != null) {
                showError(errorToString(error))
            } else {
                webView.loadUrl(url)
            }
        }
    }

    private fun showError(message: String) {
        progressBar.isVisible = false
        swipeRefresh.isRefreshing = false
        errorContainer.isVisible = true
        webView.isVisible = false
        errorText.text = message
    }

    private fun errorToString(error: ServerCheck.Error): String = when (error) {
        ServerCheck.Error.INVALID_URL -> getString(R.string.error_invalid_url)
        ServerCheck.Error.DNS_ERROR -> getString(R.string.error_dns)
        ServerCheck.Error.CONNECTION_REFUSED -> getString(R.string.error_connection_refused)
        ServerCheck.Error.TIMEOUT -> getString(R.string.error_timeout)
        ServerCheck.Error.SSL_UNRECOGNIZED_NAME -> getString(R.string.error_ssl_unrecognized)
        ServerCheck.Error.SSL_CERTIFICATE -> getString(R.string.error_ssl_certificate)
        ServerCheck.Error.SSL_OTHER -> getString(R.string.error_ssl_other)
        ServerCheck.Error.BACKEND_DOWN -> getString(R.string.error_backend_down)
        ServerCheck.Error.SERVICE_UNAVAILABLE -> getString(R.string.error_service_unavailable)
        ServerCheck.Error.GATEWAY_TIMEOUT -> getString(R.string.error_gateway_timeout)
        ServerCheck.Error.SERVER_ERROR -> getString(R.string.error_server, 0)
        ServerCheck.Error.UNKNOWN -> getString(R.string.error_generic)
    }

    private fun mapError(code: Int): String = when (code) {
        WebViewClient.ERROR_HOST_LOOKUP -> getString(R.string.error_dns)
        WebViewClient.ERROR_CONNECT -> getString(R.string.error_connection_refused)
        WebViewClient.ERROR_TIMEOUT -> getString(R.string.error_timeout)
        WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> getString(R.string.error_ssl_other)
        WebViewClient.ERROR_BAD_URL -> getString(R.string.error_invalid_url)
        WebViewClient.ERROR_UNSUPPORTED_SCHEME -> getString(R.string.error_invalid_url)
        WebViewClient.ERROR_TOO_MANY_REQUESTS -> getString(R.string.error_generic)
        else -> getString(R.string.error_generic)
    }

    private fun showServerDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.server_dialog_hint)
            setText(urlStore.get().orEmpty())
            setSingleLine(true)
        }
        val container = FrameLayout(this).apply {
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.server_dialog_title))
            .setMessage(getString(R.string.server_dialog_message))
            .setView(container)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.server_dialog_positive)) { _, _ ->
                val normalized = ServerUrlStore.normalize(input.text.toString())
                if (normalized == null) {
                    Toast.makeText(
                        this,
                        getString(R.string.server_invalid),
                        Toast.LENGTH_LONG
                    ).show()
                    showServerDialog()
                } else {
                    urlStore.save(normalized)
                    loadUrl(normalized)
                }
            }
            .setNegativeButton(getString(R.string.server_dialog_negative)) { _, _ ->
                if (urlStore.get().isNullOrBlank()) finish()
            }
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_name))
            .setMessage(getString(R.string.about_message, BuildConfig.VERSION_NAME))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun clearSession() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_session_title))
            .setMessage(getString(R.string.clear_session_message))
            .setPositiveButton(getString(R.string.clear_session_positive)) { _, _ ->
                CookieManager.getInstance().apply {
                    removeAllCookies(null)
                    flush()
                }
                WebStorage.getInstance().deleteAllData()
                webView.clearCache(true)
                webView.clearHistory()
                webView.clearFormData()
                WebViewDatabase.getInstance(this)
                    .clearHttpAuthUsernamePassword()

                val url = urlStore.get()
                if (url.isNullOrBlank()) {
                    showServerDialog()
                } else {
                    loadUrl(url)
                }
                Toast.makeText(this, getString(R.string.clear_session_done), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.server_dialog_negative), null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_change_server -> {
                showServerDialog()
                true
            }
            R.id.action_reload -> {
                webView.reload()
                true
            }
            R.id.action_clear_session -> {
                clearSession()
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }
}