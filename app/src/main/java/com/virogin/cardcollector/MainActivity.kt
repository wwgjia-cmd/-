package com.virogin.cardcollector

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : Activity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null

    private val appHost = "appassets.androidplatform.net"
    private val reqPick = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#EEF3F1")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        web = WebView(this)
        setContentView(web)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.databaseEnabled = true
        web.settings.allowFileAccess = false
        // 不跟随系统“大字体”放大页面，避免小米手机上显示过大
        web.settings.textZoom = 100
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true
        web.settings.setSupportZoom(false)
        web.addJavascriptInterface(Bridge(), "Android")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == appHost) return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                    true
                } catch (e: Exception) {
                    true
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                openPicker(params.isCaptureEnabled, params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                return true
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("确定") { _, _ -> result.confirm() }
                    .setNegativeButton("取消") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }

            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("好") { _, _ -> result.confirm() }
                    .setOnCancelListener { result.confirm() }
                    .show()
                return true
            }
        }

        if (savedInstanceState == null) {
            web.loadUrl("https://$appHost/assets/index.html")
        } else {
            web.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    private fun openPicker(capture: Boolean, multiple: Boolean) {
        val shotDir = File(cacheDir, "shots").apply { mkdirs() }
        val photo = File(shotDir, "card_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.files", photo)
        cameraUri = uri

        val camIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val intent = if (capture) {
            camIntent
        } else {
            val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                if (multiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            Intent.createChooser(pick, "选择名片照片").apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camIntent))
            }
        }

        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, reqPick)
        } catch (e: Exception) {
            fileCallback?.onReceiveValue(null)
            fileCallback = null
            Toast.makeText(this, "打不开相机或相册", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != reqPick) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        val cb = fileCallback ?: return
        fileCallback = null
        if (resultCode != RESULT_OK) {
            cb.onReceiveValue(null)
            return
        }
        val uris = mutableListOf<Uri>()
        val clip = data?.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
        } else {
            data?.data?.let { uris.add(it) }
        }
        if (uris.isEmpty()) cameraUri?.let { uris.add(it) }
        cb.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("(window.__onBack && window.__onBack()) ? 'y' : 'n'") { r ->
            if (r?.contains("y") != true) {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    private fun reply(id: String, result: String?, error: String?) {
        val js = "window.__nativeReply(" + JSONObject.quote(id) + "," +
            (if (result == null) "null" else JSONObject.quote(result)) + "," +
            (if (error == null) "null" else JSONObject.quote(error)) + ")"
        web.post { web.evaluateJavascript(js, null) }
    }

    inner class Bridge {

        /** 离线识别：返回每一行文字和它的高度/位置（JSON 字符串），通过 __nativeReply 回调。 */
        @JavascriptInterface
        fun ocr(id: String, base64: String) {
            try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp == null) {
                    reply(id, null, "decode")
                    return
                }
                val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                recognizer.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener { text ->
                        val arr = JSONArray()
                        for (block in text.textBlocks) {
                            for (line in block.lines) {
                                val box = line.boundingBox
                                val o = JSONObject()
                                o.put("text", line.text)
                                o.put("h", box?.height() ?: 0)
                                o.put("y", box?.top ?: 0)
                                arr.put(o)
                            }
                        }
                        reply(id, arr.toString(), null)
                        recognizer.close()
                    }
                    .addOnFailureListener { e ->
                        reply(id, null, e.message ?: "ocr")
                        recognizer.close()
                    }
            } catch (e: Exception) {
                reply(id, null, e.message ?: "ocr")
            }
        }

        /** 保存到手机“下载”文件夹，返回保存位置；失败返回空字符串。 */
        @JavascriptInterface
        fun saveFile(name: String, base64: String): String {
            return try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, XLSX)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return ""
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    "下载/$name"
                } else {
                    val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    val f = File(dir, name)
                    f.writeBytes(bytes)
                    f.absolutePath
                }
            } catch (e: Exception) {
                ""
            }
        }

        /** 通过系统分享发送文件（微信、邮件等）。 */
        @JavascriptInterface
        fun shareFile(name: String, base64: String): Boolean {
            return try {
                val dir = File(cacheDir, "share").apply { mkdirs() }
                val f = File(dir, name)
                f.writeBytes(Base64.decode(base64, Base64.DEFAULT))
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", f)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = XLSX
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread { startActivity(Intent.createChooser(send, "发送 Excel")) }
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    companion object {
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
