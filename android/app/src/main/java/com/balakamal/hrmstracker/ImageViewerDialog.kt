package com.balakamal.hrmstracker

import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.CookieManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageViewerDialog(
    context: Context,
    private val imageUrl: String,
    private val previewUrl: String? = null,
    private val displayTitle: String? = null
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    // Overload for single-URL callers
    constructor(context: Context, imageUrl: String, title: String? = null) :
            this(context, imageUrl, null, title)

    private lateinit var zoomImageView: ZoomableImageView
    private lateinit var progressView: ProgressBar
    private lateinit var errorView: TextView
    private lateinit var titleView: TextView
    private lateinit var resolutionBadge: TextView
    private lateinit var btnClose: ImageButton
    private lateinit var btnReset: Button
    private lateinit var btnShare: ImageButton
    private lateinit var btnDownload: ImageButton
    private lateinit var hintView: TextView

    private var loadedBitmap: Bitmap? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_image_viewer)

        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        zoomImageView = findViewById(R.id.viewer_image)
        progressView = findViewById(R.id.viewer_progress)
        errorView = findViewById(R.id.viewer_error_text)
        titleView = findViewById(R.id.viewer_title)
        resolutionBadge = findViewById(R.id.viewer_resolution_badge)
        btnClose = findViewById(R.id.viewer_btn_close)
        btnReset = findViewById(R.id.viewer_btn_reset)
        btnShare = findViewById(R.id.viewer_btn_share)
        btnDownload = findViewById(R.id.viewer_btn_download)
        hintView = findViewById(R.id.viewer_hint)

        // Title handling
        val cleanTitle = if (!displayTitle.isNullOrBlank() && displayTitle != "null") {
            displayTitle.trim()
        } else {
            extractFilenameFromUrl(imageUrl)
        }
        titleView.text = cleanTitle

        btnClose.setOnClickListener { dismiss() }
        btnReset.setOnClickListener { zoomImageView.resetZoom() }

        btnShare.setOnClickListener { shareImage() }
        btnDownload.setOnClickListener { saveImageToDevice() }

        // Fade hint after 3 seconds
        mainHandler.postDelayed({
            hintView.animate().alpha(0f).setDuration(600).withEndAction {
                hintView.visibility = View.GONE
            }.start()
        }, 3000)

        loadImagesTwoStage()
    }

    private fun extractFilenameFromUrl(url: String): String {
        return try {
            val cleanUrl = url.split("?")[0]
            val uri = Uri.parse(cleanUrl)
            val lastSegment = uri.lastPathSegment
            if (!lastSegment.isNullOrBlank() && lastSegment.contains(".")) {
                lastSegment
            } else {
                "HRMS_Image_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            }
        } catch (e: Exception) {
            "HRMS_Image"
        }
    }

    /**
     * Two-stage loader:
     * 1. If a previewUrl (thumbnail/DOM image) is available, decode/display it immediately.
     * 2. In parallel, fetch the uncompressed original high-res image with portal cookies & tokens.
     */
    private fun loadImagesTwoStage() {
        errorView.visibility = View.GONE
        zoomImageView.visibility = View.VISIBLE

        val hasDistinctPreview = !previewUrl.isNullOrBlank() && previewUrl != imageUrl

        if (hasDistinctPreview) {
            resolutionBadge.text = "Loading preview..."
            resolutionBadge.visibility = View.VISIBLE

            // Stage 1: Load thumbnail immediately
            Thread {
                val previewBitmap = if (previewUrl!!.startsWith("data:image/")) {
                    decodeBase64Image(previewUrl)
                } else {
                    fetchHttpImage(previewUrl)
                }

                mainHandler.post {
                    if (previewBitmap != null && loadedBitmap == null) {
                        loadedBitmap = previewBitmap
                        zoomImageView.setImageBitmap(previewBitmap)
                        progressView.visibility = View.GONE
                        resolutionBadge.text = "Enhancing to crisp original..."
                    }
                }
            }.start()
        } else {
            progressView.visibility = View.VISIBLE
            resolutionBadge.text = "Loading original..."
        }

        // Stage 2: Fetch crisp original image
        Thread {
            try {
                val originalBitmap: Bitmap? = if (imageUrl.startsWith("data:image/")) {
                    decodeBase64Image(imageUrl)
                } else {
                    fetchHttpImage(imageUrl)
                }

                mainHandler.post {
                    progressView.visibility = View.GONE
                    if (originalBitmap != null) {
                        loadedBitmap = originalBitmap
                        zoomImageView.setImageBitmap(originalBitmap)
                        resolutionBadge.text = "${originalBitmap.width} × ${originalBitmap.height} • Crisp Original"
                        resolutionBadge.setTextColor(0xFF7DE8B3.toInt()) // subtle mint green
                    } else if (loadedBitmap == null) {
                        // Neither original nor preview could be loaded
                        errorView.visibility = View.VISIBLE
                        resolutionBadge.visibility = View.GONE
                    } else {
                        // Original failed, but preview is active
                        resolutionBadge.text = "Preview Mode (${loadedBitmap?.width} × ${loadedBitmap?.height})"
                        resolutionBadge.setTextColor(0xFFA0A5BA.toInt())
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post {
                    progressView.visibility = View.GONE
                    if (loadedBitmap == null) {
                        errorView.visibility = View.VISIBLE
                        errorView.text = "Failed to load image: ${e.localizedMessage}"
                        resolutionBadge.visibility = View.GONE
                    } else {
                        resolutionBadge.text = "Preview Mode"
                    }
                }
            }
        }.start()
    }

    private fun decodeBase64Image(dataUri: String): Bitmap? {
        val commaIndex = dataUri.indexOf(",")
        if (commaIndex == -1) return null
        val base64Data = dataUri.substring(commaIndex + 1)
        val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, options)
    }

    private fun fetchHttpImage(urlString: String): Bitmap? {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        try {
            connection = TokenManager.openConnection(context, urlString)
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.instanceFollowRedirects = true

            // Set modern browser headers
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            connection.setRequestProperty("Referer", "https://apps.pal.tech/hrms/")

            // Attach active session cookies from WebView CookieManager
            try {
                val cookieManager = CookieManager.getInstance()
                val cookies = cookieManager.getCookie(urlString)
                if (!cookies.isNullOrBlank()) {
                    connection.setRequestProperty("Cookie", cookies)
                }
            } catch (e: Exception) {
                // Ignore cookie errors
            }

            // Attach Bearer token if HRMS domain
            val token = TokenManager.getValidAccessToken(context)
            if (token != null && (urlString.contains("pal.tech") || urlString.contains("hrms"))) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }

            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = connection.inputStream
                val byteBuffer = ByteArrayOutputStream()
                val buffer = ByteArray(16384)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    byteBuffer.write(buffer, 0, bytesRead)
                }
                val imageBytes = byteBuffer.toByteArray()

                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            inputStream?.close()
            connection?.disconnect()
        }
        return null
    }

    private fun saveImageToDevice() {
        val bitmap = loadedBitmap
        if (bitmap == null) {
            Toast.makeText(context, "Image still loading...", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val filename = "HRMS_${timestamp}.png"

                val success: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HRMS")
                    }
                    val resolver = context.contentResolver
                    val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (imageUri != null) {
                        resolver.openOutputStream(imageUri).use { out ->
                            if (out != null) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) else false
                        }
                    } else false
                } else {
                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val hrmsDir = File(picturesDir, "HRMS").apply { if (!exists()) mkdirs() }
                    val file = File(hrmsDir, filename)
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    // Notify media scanner
                    val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                        data = Uri.fromFile(file)
                    }
                    context.sendBroadcast(intent)
                    true
                }

                mainHandler.post {
                    if (success) {
                        Toast.makeText(context, "Saved image to Pictures/HRMS", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post {
                    Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun shareImage() {
        val bitmap = loadedBitmap
        if (bitmap != null) {
            try {
                val cachePath = File(context.cacheDir, "shared_images").apply { if (!exists()) mkdirs() }
                val tempFile = File(cachePath, "shared_hrms_image.png")
                FileOutputStream(tempFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Image via"))
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback: share direct URL
        if (imageUrl.startsWith("http")) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, imageUrl)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Image Link via"))
        }
    }
}
