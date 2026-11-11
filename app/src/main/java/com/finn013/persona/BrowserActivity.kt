package com.finn013.persona

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils

/** Окно одного профиля. Каждый профиль (и каждый ярлык) — отдельное окно в «Недавних». */
class BrowserActivity : AppCompatActivity() {

    private lateinit var profile: Profile
    private lateinit var web: WebView
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var container: FrameLayout
    private lateinit var fullscreen: FrameLayout
    private val popups = mutableListOf<WebView>()
    private var desktop = false
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        fileCallback = null
    }

    private val current: WebView get() = popups.lastOrNull() ?: web

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent.data
        val p = Profiles.get(this, data?.getQueryParameter("p"))
        if (p == null) {
            Toast.makeText(this, "Профиль не найден", Toast.LENGTH_SHORT).show()
            finishAndRemoveTask(); return
        }
        profile = p
        buildUi()
        setTaskDescription(ActivityManager.TaskDescription(profile.name, 0, profile.color))

        web = newWebView()
        container.addView(web)
        val start = data?.getQueryParameter("u") ?: profile.startUrl.ifBlank { HOME }
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) web.loadUrl(normalize(start))

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> customCallback?.onCustomViewHidden()
                    current.canGoBack() -> current.goBack()
                    popups.isNotEmpty() -> closePopup(popups.last())
                    else -> moveTaskToBack(true) // окно остаётся живым, как вкладка
                }
            }
        })
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tint = ColorUtils.setAlphaComponent(profile.color, 40)

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(4), dp(6))
            setBackgroundColor(tint)
        }
        val badge = TextView(this).apply {
            text = profile.name.take(12)
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 12f
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setBackgroundResource(R.drawable.pill)
            backgroundTintList = ColorStateList.valueOf(profile.color)
            setOnClickListener { startActivity(Intent(this@BrowserActivity, MainActivity::class.java)) }
        }
        bar.addView(badge)
        urlBar = EditText(this).apply {
            setSingleLine()
            textSize = 14f
            inputType = InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_GO
            setSelectAllOnFocus(true)
            setBackgroundResource(R.drawable.url_bg)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            hint = "Адрес или поиск"
            setOnEditorActionListener { v, id, ev ->
                if (id == EditorInfo.IME_ACTION_GO || ev?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    current.loadUrl(normalize(v.text.toString()))
                    current.requestFocus()
                    getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(v.windowToken, 0)
                    true
                } else false
            }
        }
        bar.addView(urlBar, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(8), 0, dp(2), 0) })
        val more = ImageButton(this).apply {
            setImageResource(R.drawable.ic_more)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { showMenu(it) }
        }
        bar.addView(more, LinearLayout.LayoutParams(dp(44), dp(44)))
        col.addView(bar)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(profile.color)
            visibility = View.GONE
        }
        col.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))

        container = FrameLayout(this)
        col.addView(container, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(col)

        fullscreen = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); visibility = View.GONE }
        root.addView(fullscreen, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun newWebView(): WebView {
        val w = WebView(this)
        Engine.setup(this, w, profile, desktop)
        w.webViewClient = client
        w.webChromeClient = chrome
        w.setDownloadListener { url, ua, cd, mime, _ -> download(url, ua, cd, mime) }
        return w
    }

    private val client = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val u = request.url
            return when (u.scheme?.lowercase()) {
                "http", "https", "about", "data", "blob", "javascript" -> false
                else -> { openExternal(u.toString()); true }
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (view == current && !urlBar.hasFocus()) urlBar.setText(url)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            if (view == current && !urlBar.hasFocus()) urlBar.setText(url)
        }
    }

    private val chrome = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (view != current) return
            progress.progress = newProgress
            progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (view == web && !title.isNullOrBlank()) {
                setTaskDescription(ActivityManager.TaskDescription("${profile.name} · $title", 0, profile.color))
            }
        }

        // Всплывающие окна (вход через Google/VK/Telegram и т.п.) — в том же профиле.
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            val child = newWebView()
            container.addView(child, FrameLayout.LayoutParams(-1, -1))
            popups.add(child)
            (resultMsg.obj as WebView.WebViewTransport).webView = child
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) = closePopup(window)

        override fun onShowFileChooser(view: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = cb
            return try {
                filePicker.launch(params.createIntent()); true
            } catch (e: ActivityNotFoundException) {
                fileCallback = null; false
            }
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            customView = view
            customCallback = callback
            fullscreen.addView(view, FrameLayout.LayoutParams(-1, -1))
            fullscreen.visibility = View.VISIBLE
        }

        override fun onHideCustomView() {
            fullscreen.removeAllViews()
            fullscreen.visibility = View.GONE
            customView = null
            customCallback = null
        }
    }

    private fun closePopup(w: WebView) {
        if (!popups.remove(w)) return
        container.removeView(w)
        w.destroy()
        urlBar.setText(current.url ?: "")
    }

    private fun showMenu(anchor: View) {
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 1, 0, "← Назад").setEnabled(current.canGoBack())
        m.menu.add(0, 2, 1, "Вперёд →").setEnabled(current.canGoForward())
        m.menu.add(0, 3, 2, "Обновить")
        m.menu.add(0, 4, 3, "Домашняя страница")
        m.menu.add(0, 5, 4, "Ярлык на рабочий стол")
        m.menu.add(0, 6, 5, if (desktop) "Мобильная версия" else "Версия для ПК")
        m.menu.add(0, 7, 6, "Открыть в другом приложении")
        m.menu.add(0, 8, 7, "Все профили")
        m.menu.add(0, 9, 8, "Закрыть окно")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> current.goBack()
                2 -> current.goForward()
                3 -> current.reload()
                4 -> web.loadUrl(normalize(profile.startUrl.ifBlank { HOME }))
                5 -> askShortcut()
                6 -> toggleDesktop()
                7 -> current.url?.let { u -> openExternal(u) }
                8 -> startActivity(Intent(this, MainActivity::class.java))
                9 -> finishAndRemoveTask()
            }
            true
        }
        m.show()
    }

    private fun askShortcut() {
        val url = current.url ?: return
        val input = EditText(this).apply {
            setText(current.title?.take(24) ?: profile.name)
            setSelectAllOnFocus(true)
        }
        val box = FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(input) }
        AlertDialog.Builder(this)
            .setTitle("Ярлык на рабочий стол")
            .setMessage("Откроется отдельным окном в профиле «${profile.name}».")
            .setView(box)
            .setPositiveButton("Добавить") { _, _ ->
                Shortcuts.pin(this, profile, url, input.text.toString().trim(), current.favicon)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun toggleDesktop() {
        desktop = !desktop
        val url = web.url
        popups.toList().forEach { closePopup(it) }
        container.removeView(web)
        web.destroy()
        web = newWebView()
        container.addView(web, 0)
        web.loadUrl(url ?: normalize(profile.startUrl.ifBlank { HOME }))
    }

    private fun download(url: String, ua: String, cd: String?, mime: String?) {
        if (!url.startsWith("http")) {
            Toast.makeText(this, "Этот файл скачать нельзя (blob/data)", Toast.LENGTH_SHORT).show(); return
        }
        try {
            val name = URLUtil.guessFileName(url, cd, mime)
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                Engine.cookies(profile).getCookie(url)?.let { addRequestHeader("Cookie", it) }
                addRequestHeader("User-Agent", ua)
                setMimeType(mime)
                setTitle(name)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            }
            getSystemService(DownloadManager::class.java).enqueue(req)
            Toast.makeText(this, "Скачивание: $name", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось скачать: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openExternal(url: String) {
        try {
            val i = if (url.startsWith("intent:")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            else Intent(Intent.ACTION_VIEW, Uri.parse(url))
            i.addCategory(Intent.CATEGORY_BROWSABLE)
            i.component = null
            i.selector = null
            startActivity(i)
        } catch (e: Exception) {
            if (url.startsWith("intent:")) {
                val fallback = Regex("S\\.browser_fallback_url=([^;]+)").find(url)?.groupValues?.get(1)
                if (fallback != null) { current.loadUrl(Uri.decode(fallback)); return }
            }
            Toast.makeText(this, "Нет приложения для этой ссылки", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::web.isInitialized) web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        if (::profile.isInitialized) Engine.cookies(profile).flush()
    }

    override fun onDestroy() {
        if (::web.isInitialized) {
            popups.forEach { it.destroy() }
            popups.clear()
            web.destroy()
        }
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val HOME = "https://www.google.com"

        fun normalize(input: String): String {
            val t = input.trim()
            if (t.isEmpty()) return HOME
            if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(t) || t.startsWith("about:")) return t
            if (!t.contains(' ') && (t.contains('.') || t.startsWith("localhost"))) return "https://$t"
            return "https://www.google.com/search?q=" + Uri.encode(t)
        }
    }
}
