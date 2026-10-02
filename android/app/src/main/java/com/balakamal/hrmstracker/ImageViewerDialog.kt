package com.balakamal.hrmstracker

import android.app.Dialog
import android.app.DownloadManager
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
import java.io.OutputStream
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageViewerDialog(
    context: Context,
    private val imageUrl: String,
    private val displayTitle: String? = null
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    private lateinit var zoomImageView: ZoomableImageView
    private lateinit var progressView: ProgressBar
    private lateinit var errorView: TextView
    private lateinit var titleView: TextView
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

        loadImage()
    }

    private fun extractFilenameFromUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
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

    private fun loadImage() {
        progressView.visibility = View.VISIBLE
        errorView.visibility = View.GONE
        zoomImageView.visibility = View.VISIBLE

        Thread {
            try {
                val bitmap: Bitmap? = if (imageUrl.startsWith("data:image/")) {
                    decodeBase64Image(imageUrl)
                } else {
                    fetchHttpImage(imageUrl)
                }

                mainHandler.post {
                    if (bitmap != null) {
                        loadedBitmap = bitmap
                        progressView.visibility = View.GONE
                        zoomImageView.setImageBitmap(bitmap)
                    } else {
                        progressView.visibility = View.GONE
                        errorView.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post {
                    progressView.visibility = View.GONE
                    errorView.visibility = View.VISIBLE
                    errorView.text = "Failed to load image: ${e.localizedMessage}"
                }
            }
        }.start()
    }

    private fun decodeBase64Image(dataUri: String): Bitmap? {
        val commaIndex = dataUri.indexOf(",")
        if (commaIndex == -1) return null
        val base64Data = dataUri.substring(commaIndex + 1)
        val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }

    private fun fetchHttpImage(urlString: String): Bitmap? {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        try {
            connection = TokenManager.openConnection(context, urlString)
            connection.requestMethod = "GET"
            connection.connectTimeout = 12000
            connection.readTimeout = 15000

            // If it's on pal.tech domain, attach Bearer auth token if available
            val token = TokenManager.getValidAccessToken(context)
            if (token != null && (urlString.contains("pal.tech") || urlString.contains("hrms"))) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }

            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = connection.inputStream
                // Read fully into byte array to calculate bitmap dimensions accurately
                val byteBuffer = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    byteBuffer.write(buffer, 0, bytesRead)
                }
                val imageBytes = byteBuffer.toByteArray()

                // Decode bounds first to verify image
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
            }
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

        // Fallback: share the direct URL
        if (imageUrl.startsWith("http")) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, imageUrl)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Image Link via"))
        }
    }
}
