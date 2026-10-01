package com.balakamal.hrmstracker

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Build
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Centralized TokenManager responsible for managing, inspecting, and automatically
 * rotating JWT AccessTokens and RefreshTokens for HRMS Tracker.
 */
object TokenManager {

    private const val TAG = "HRMS_TokenManager"
    const val PREFS_NAME = "HRMS_PREFS"
    const val KEY_ACCESS_TOKEN = "AccessToken"
    const val KEY_REFRESH_TOKEN = "RefreshToken"
    private const val REFRESH_ENDPOINT = "https://apps.pal.tech/hrms-backend/api/Account/RefreshToken"

    sealed class RefreshResult {
        data class Success(val accessToken: String, val refreshToken: String) : RefreshResult()
        data class SessionExpired(val reason: String) : RefreshResult()
        data class NetworkError(val message: String) : RefreshResult()
    }

    /**
     * Inspects the JWT token payload and checks if it is expired or within [bufferSeconds] of expiry.
     */
    fun isTokenExpired(token: String?, bufferSeconds: Long = 60): Boolean {
        if (token.isNullOrEmpty()) return true
        try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val rawPayload = parts[1]
                val padded = when (rawPayload.length % 4) {
                    2 -> "$rawPayload=="
                    3 -> "$rawPayload="
                    else -> rawPayload
                }
                val payloadBytes = Base64.decode(
                    padded,
                    Base64.URL_SAFE or Base64.NO_WRAP
                )
                val payloadString = String(payloadBytes, StandardCharsets.UTF_8)
                val json = JSONObject(payloadString)
                val exp = json.optLong("exp", 0L)
                if (exp > 0) {
                    val nowSec = System.currentTimeMillis() / 1000
                    return nowSec >= (exp - bufferSeconds)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking token expiration: ${e.message}")
        }
        return false
    }

    /**
     * Helper to open an HttpURLConnection respecting the active network on modern Android.
     */
    fun openConnection(context: Context, apiUrl: String): HttpURLConnection {
        val url = URL(apiUrl)
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && connectivityManager != null) {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                return activeNetwork.openConnection(url) as HttpURLConnection
            }
        }
        return url.openConnection() as HttpURLConnection
    }

    /**
     * Retrieves the current AccessToken, proactively refreshing it if it has expired.
     */
    @Synchronized
    fun getValidAccessToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)

        if (currentToken.isNullOrEmpty() && refreshToken.isNullOrEmpty()) {
            return null
        }

        // If the current token is missing or expired, attempt silent rotation proactively
        if (isTokenExpired(currentToken)) {
            Log.d(TAG, "AccessToken is expired or expiring soon. Performing proactive rotation...")
            val result = refreshTokens(context)
            if (result is RefreshResult.Success) {
                return result.accessToken
            } else if (result is RefreshResult.SessionExpired) {
                Log.w(TAG, "Proactive token refresh rejected: ${result.reason}")
                return null
            }
            // In case of NetworkError, return existing token as best-effort fallback
            return currentToken
        }

        return currentToken
    }

    /**
     * Executes the silent token refresh request against HRMS SSO endpoint.
     * Synchronized to prevent duplicate parallel refresh requests from widgets and background workers.
     */
    @Synchronized
    fun refreshTokens(context: Context): RefreshResult {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)

        if (refreshToken.isNullOrEmpty()) {
            return RefreshResult.SessionExpired("No RefreshToken found in SharedPreferences")
        }

        Log.d(TAG, "Calling SSO token refresh endpoint: $REFRESH_ENDPOINT")
        var connection: HttpURLConnection? = null
        return try {
            connection = openConnection(context, REFRESH_ENDPOINT)
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true

            // Send payload {"refreshToken": "..."}
            val requestBody = JSONObject().apply {
                put("refreshToken", refreshToken)
            }.toString()

            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Token refresh response code: $responseCode")

            if (responseCode == 200) {
                val responseString = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    reader.readText()
                }

                val jsonResponse = JSONObject(responseString)
                val dataObj = jsonResponse.optJSONObject("data") ?: jsonResponse

                val newAccessToken = (dataObj.optString("access_token").takeIf { it.isNotEmpty() }
                    ?: dataObj.optString("accessToken")).trim()
                val newRefreshToken = (dataObj.optString("refresh_token").takeIf { it.isNotEmpty() }
                    ?: dataObj.optString("refreshToken")).trim()

                if (newAccessToken.isNotEmpty() && newAccessToken != "null") {
                    val editor = prefs.edit()
                    editor.putString(KEY_ACCESS_TOKEN, newAccessToken)

                    // If a newly rotated refresh token is returned, update it; otherwise preserve old one
                    val finalRefresh = if (newRefreshToken.isNotEmpty() && newRefreshToken != "null") {
                        editor.putString(KEY_REFRESH_TOKEN, newRefreshToken)
                        newRefreshToken
                    } else {
                        refreshToken
                    }
                    editor.apply()

                    Log.i(TAG, "Successfully refreshed and rotated HRMS auth tokens.")
                    RefreshResult.Success(newAccessToken, finalRefresh)
                } else {
                    Log.e(TAG, "Token refresh response missing access_token: $responseString")
                    RefreshResult.SessionExpired("Invalid token refresh response payload")
                }
            } else if (responseCode == 400 || responseCode == 401 || responseCode == 403) {
                // Refresh token is revoked or permanently expired
                Log.w(TAG, "Refresh token expired or rejected by server: HTTP $responseCode")
                RefreshResult.SessionExpired("Refresh token rejected (HTTP $responseCode)")
            } else {
                RefreshResult.NetworkError("Server returned HTTP $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error during token refresh: ${e.message}", e)
            RefreshResult.NetworkError(e.message ?: "Unknown network exception")
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Injects the latest native tokens into the WebView's localStorage.
     */
    fun syncTokensToWebView(webView: WebView, context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: ""

        val jsScript = """
            (function() {
                try {
                    var curAccess = localStorage.getItem('AccessToken');
                    var curRefresh = localStorage.getItem('RefreshToken');
                    var newAccess = ${JSONObject.quote(accessToken)};
                    var newRefresh = ${JSONObject.quote(refreshToken)};
                    var changed = false;
                    if (newAccess && curAccess !== newAccess) {
                        localStorage.setItem('AccessToken', newAccess);
                        changed = true;
                    }
                    if (newRefresh && curRefresh !== newRefresh) {
                        localStorage.setItem('RefreshToken', newRefresh);
                        changed = true;
                    }
                    if (changed && window.atReloadAttendance) {
                        window.atReloadAttendance();
                    }
                } catch(e) {}
            })();
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(jsScript, null)
        }
    }
}
