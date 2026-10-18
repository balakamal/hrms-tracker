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
    const val KEY_AUTH_COOKIES = "AuthCookies"
    private const val SSO_TOKEN_ENDPOINT = "https://sso.pal.tech/token"
    private const val SSO_CLIENT_ID = "sso_prod"

    const val KEY_USER_ID = "UserId"
    const val KEY_USER_NAME = "UserName"
    private const val GET_MY_DETAILS_ENDPOINT = "https://apps.pal.tech/hrms-backend/api/Employee/GetMyDetails"

    sealed class RefreshResult {
        data class Success(val accessToken: String, val refreshToken: String) : RefreshResult()
        data class SessionExpired(val reason: String) : RefreshResult()
        data class NetworkError(val message: String) : RefreshResult()
    }

    /**
     * Parses the value of a specific cookie key from a raw Cookie header string.
     */
    fun extractCookieValue(cookieHeader: String?, cookieName: String): String? {
        if (cookieHeader.isNullOrBlank()) return null
        val cookies = cookieHeader.split(";")
        for (c in cookies) {
            val trimmed = c.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val name = trimmed.substring(0, eqIdx).trim()
                if (name.equals(cookieName, ignoreCase = true)) {
                    return trimmed.substring(eqIdx + 1).trim()
                }
            }
        }
        return null
    }

    /**
     * Reads all cookies from android.webkit.CookieManager for apps.pal.tech (and sso.pal.tech),
     * extracts prod_access_token / prod_refresh_token, and caches both tokens and cookie headers.
     */
    @Synchronized
    fun extractCookiesAndTokensFromManager(context: Context): Triple<String?, String?, String?> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return try {
            val cookieManager = android.webkit.CookieManager.getInstance()
            val appCookies = cookieManager.getCookie("https://apps.pal.tech") ?: ""
            val ssoCookies = cookieManager.getCookie("https://sso.pal.tech") ?: ""

            val combinedCookies = when {
                appCookies.isNotBlank() && ssoCookies.isNotBlank() -> "$appCookies; $ssoCookies"
                appCookies.isNotBlank() -> appCookies
                else -> ssoCookies
            }

            val accessToken = extractCookieValue(combinedCookies, "prod_access_token")
                ?: extractCookieValue(combinedCookies, "AccessToken")
            val refreshToken = extractCookieValue(combinedCookies, "prod_refresh_token")
                ?: extractCookieValue(combinedCookies, "RefreshToken")

            val editor = prefs.edit()
            if (combinedCookies.isNotBlank()) {
                editor.putString(KEY_AUTH_COOKIES, combinedCookies)
            }
            if (!accessToken.isNullOrBlank()) {
                editor.putString(KEY_ACCESS_TOKEN, accessToken)
            }
            if (!refreshToken.isNullOrBlank()) {
                editor.putString(KEY_REFRESH_TOKEN, refreshToken)
            }
            editor.apply()

            Triple(accessToken, refreshToken, combinedCookies.takeIf { it.isNotBlank() })
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting cookies from CookieManager: ${e.message}")
            Triple(null, null, prefs.getString(KEY_AUTH_COOKIES, null))
        }
    }

    /**
     * Retrieves the latest active session cookies from CookieManager with SharedPreferences fallback.
     */
    fun getValidCookies(context: Context): String? {
        try {
            val cookieManager = android.webkit.CookieManager.getInstance()
            val cookies = cookieManager.getCookie("https://apps.pal.tech")
            if (!cookies.isNullOrBlank()) {
                return cookies
            }
        } catch (e: Exception) {
            // Fallback to prefs
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_AUTH_COOKIES, null)
    }

    /**
     * Decodes and parses the payload segment of a JWT token safely into a JSONObject.
     */
    fun decodeJwtPayload(token: String?): JSONObject? {
        if (token.isNullOrEmpty()) return null
        return try {
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
                JSONObject(payloadString)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding JWT payload: ${e.message}")
            null
        }
    }

    /**
     * Inspects the JWT token payload and checks if it is expired or within [bufferSeconds] of expiry.
     */
    fun isTokenExpired(token: String?, bufferSeconds: Long = 60): Boolean {
        if (token.isNullOrEmpty()) return true
        val json = decodeJwtPayload(token) ?: return true
        val exp = json.optLong("exp", 0L)
        if (exp > 0) {
            val nowSec = System.currentTimeMillis() / 1000
            return nowSec >= (exp - bufferSeconds)
        }
        return false
    }

    /**
     * Extracts user id and user name claims directly from JWT token payload as an offline fallback.
     */
    fun extractIdentityFromJwt(token: String?): Pair<String, String>? {
        val payload = decodeJwtPayload(token) ?: return null
        val id = listOf("nameid", "sub", "id", "employeeId", "EmployeeNumber", "name_id", "userId")
            .firstNotNullOfOrNull { key -> payload.optString(key).takeIf { it.isNotBlank() && it != "null" } }
        val name = listOf("name", "unique_name", "fullName", "given_name", "email")
            .firstNotNullOfOrNull { key -> payload.optString(key).takeIf { it.isNotBlank() && it != "null" } }
            ?: "User"
        return if (!id.isNullOrBlank()) Pair(id, name) else null
    }

    /**
     * Resolves the authenticated user's id and name natively using the official HRMS API.
     * Caches the resolved credentials in SharedPreferences (UserId, UserName).
     * Falls back to offline JWT claims parsing if network or endpoint fails.
     */
    @Synchronized
    fun fetchUserIdentity(context: Context, accessToken: String? = null): Pair<String, String>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = accessToken ?: getValidAccessToken(context)

        var connection: HttpURLConnection? = null
        try {
            Log.d(TAG, "Resolving user identity natively from: $GET_MY_DETAILS_ENDPOINT")
            connection = openAuthenticatedConnection(context, GET_MY_DETAILS_ENDPOINT, token)
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val responseString = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use {
                    it.readText()
                }
                val jsonResponse = JSONObject(responseString)
                val dataObj = jsonResponse.optJSONObject("data") ?: jsonResponse

                val id = (dataObj.optString("id").takeIf { it.isNotBlank() && it != "null" }
                    ?: dataObj.optString("employeeId").takeIf { it.isNotBlank() && it != "null" }
                    ?: dataObj.optString("userId")).trim()

                val name = (dataObj.optString("name").takeIf { it.isNotBlank() && it != "null" }
                    ?: dataObj.optString("firstName").takeIf { it.isNotBlank() && it != "null" }
                    ?: "User").trim()

                if (id.isNotEmpty() && id != "null") {
                    prefs.edit()
                        .putString(KEY_USER_ID, id)
                        .putString(KEY_USER_NAME, name)
                        .apply()
                    Log.i(TAG, "Successfully resolved and cached user identity: id=$id, name=$name")
                    return Pair(id, name)
                }
            } else {
                Log.w(TAG, "GetMyDetails responded with HTTP $responseCode")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network exception during fetchUserIdentity: ${e.message}")
        } finally {
            connection?.disconnect()
        }

        // Fallback: Extract from JWT token claims
        if (!token.isNullOrBlank()) {
            val jwtIdentity = extractIdentityFromJwt(token)
            if (jwtIdentity != null) {
                prefs.edit()
                    .putString(KEY_USER_ID, jwtIdentity.first)
                    .putString(KEY_USER_NAME, jwtIdentity.second)
                    .apply()
                Log.i(TAG, "Resolved user identity from JWT payload: id=${jwtIdentity.first}, name=${jwtIdentity.second}")
                return jwtIdentity
            }
        }

        return null
    }

    /**
     * Ensures UserId and UserName are present in SharedPreferences.
     * If absent, automatically invokes fetchUserIdentity with the active access token.
     */
    fun ensureUserIdentity(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingId = prefs.getString(KEY_USER_ID, null)
        val existingName = prefs.getString(KEY_USER_NAME, "User") ?: "User"

        if (!existingId.isNullOrBlank()) {
            return Pair(existingId, existingName)
        }

        return fetchUserIdentity(context)
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
     * Opens an HttpURLConnection and automatically attaches both:
     * 1) Cookie header (from CookieManager / SharedPreferences)
     * 2) Authorization: Bearer <token> (if available)
     * 3) Standard browser/client headers
     */
    fun openAuthenticatedConnection(context: Context, apiUrl: String, accessToken: String? = null): HttpURLConnection {
        val connection = openConnection(context, apiUrl)
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")

        // 1. Attach cookies
        val cookies = getValidCookies(context)
        if (!cookies.isNullOrBlank()) {
            connection.setRequestProperty("Cookie", cookies)
        }

        // 2. Attach Bearer token
        val token = accessToken ?: getValidAccessToken(context)
        if (!token.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $token")
        }

        return connection
    }

    /**
     * Retrieves the current AccessToken, proactively refreshing it if it has expired.
     */
    @Synchronized
    fun getValidAccessToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var currentToken = prefs.getString(KEY_ACCESS_TOKEN, null)

        // If missing or expired, attempt extraction from CookieManager first
        if (currentToken.isNullOrEmpty() || isTokenExpired(currentToken)) {
            val (extractedToken, _, _) = extractCookiesAndTokensFromManager(context)
            if (!extractedToken.isNullOrEmpty() && !isTokenExpired(extractedToken)) {
                currentToken = extractedToken
            }
        }

        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        if (currentToken.isNullOrEmpty() && refreshToken.isNullOrEmpty()) {
            return null
        }

        // If the current token is missing or expired, attempt silent rotation proactively via SSO
        if (isTokenExpired(currentToken)) {
            Log.d(TAG, "AccessToken is expired or expiring soon. Performing proactive rotation via SSO...")
            val result = refreshTokens(context)
            if (result is RefreshResult.Success) {
                return result.accessToken
            } else if (result is RefreshResult.SessionExpired) {
                Log.w(TAG, "Proactive token refresh rejected: ${result.reason}")
                return null
            }
            return currentToken
        }

        return currentToken
    }

    /**
     * Executes the silent token refresh request against HRMS SSO endpoint (https://sso.pal.tech/token).
     * Synchronized to prevent duplicate parallel refresh requests from widgets and background workers.
     */
    @Synchronized
    fun refreshTokens(context: Context): RefreshResult {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)

        if (refreshToken.isNullOrEmpty()) {
            val (_, extractedRefresh, _) = extractCookiesAndTokensFromManager(context)
            refreshToken = extractedRefresh ?: prefs.getString(KEY_REFRESH_TOKEN, null)
        }

        if (refreshToken.isNullOrEmpty()) {
            return RefreshResult.SessionExpired("No RefreshToken found in SharedPreferences or CookieManager")
        }

        Log.d(TAG, "Calling SSO token refresh endpoint: $SSO_TOKEN_ENDPOINT")
        var connection: HttpURLConnection? = null
        return try {
            connection = openConnection(context, SSO_TOKEN_ENDPOINT)
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true

            // Send OAuth 2.0 / OIDC refresh token parameters
            val postData = "grant_type=" + java.net.URLEncoder.encode("refresh_token", "UTF-8") +
                    "&client_id=" + java.net.URLEncoder.encode(SSO_CLIENT_ID, "UTF-8") +
                    "&refresh_token=" + java.net.URLEncoder.encode(refreshToken, "UTF-8")

            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "SSO token refresh response code: $responseCode")

            if (responseCode == 200) {
                val responseString = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    reader.readText()
                }

                val jsonResponse = JSONObject(responseString)
                val newAccessToken = (jsonResponse.optString("access_token").takeIf { it.isNotEmpty() }
                    ?: jsonResponse.optString("accessToken")).trim()
                val newRefreshToken = (jsonResponse.optString("refresh_token").takeIf { it.isNotEmpty() }
                    ?: jsonResponse.optString("refreshToken")).trim()

                if (newAccessToken.isNotEmpty() && newAccessToken != "null") {
                    val editor = prefs.edit()
                    editor.putString(KEY_ACCESS_TOKEN, newAccessToken)

                    val finalRefresh = if (newRefreshToken.isNotEmpty() && newRefreshToken != "null") {
                        editor.putString(KEY_REFRESH_TOKEN, newRefreshToken)
                        newRefreshToken
                    } else {
                        refreshToken
                    }
                    editor.apply()

                    // Sync rotated token into native CookieManager
                    try {
                        val cookieManager = android.webkit.CookieManager.getInstance()
                        cookieManager.setCookie("https://apps.pal.tech", "prod_access_token=$newAccessToken; Domain=.pal.tech; Path=/; Secure; HttpOnly")
                        if (newRefreshToken.isNotEmpty() && newRefreshToken != "null") {
                            cookieManager.setCookie("https://apps.pal.tech", "prod_refresh_token=$newRefreshToken; Domain=.pal.tech; Path=/; Secure; HttpOnly")
                        }
                        cookieManager.flush()
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not sync rotated token to CookieManager: ${e.message}")
                    }

                    Log.i(TAG, "Successfully refreshed and rotated HRMS SSO tokens.")
                    RefreshResult.Success(newAccessToken, finalRefresh)
                } else {
                    Log.e(TAG, "SSO token refresh response missing access_token: $responseString")
                    RefreshResult.SessionExpired("Invalid token refresh response payload")
                }
            } else if (responseCode == 400 || responseCode == 401 || responseCode == 403) {
                Log.w(TAG, "Refresh token expired or rejected by SSO: HTTP $responseCode")
                RefreshResult.SessionExpired("Refresh token rejected by SSO (HTTP $responseCode)")
            } else {
                RefreshResult.NetworkError("SSO server returned HTTP $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error during SSO token refresh: ${e.message}", e)
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
