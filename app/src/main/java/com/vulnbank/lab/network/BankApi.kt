package com.vulnbank.lab.network

import com.vulnbank.lab.BuildConfig
import com.vulnbank.lab.security.LabSecrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

// VULNBANK LOCAL LAB ONLY: no Frida, no TLS certificate and no TLS pinning.
// The emulator connects to Burp on Windows at 10.0.2.2:8081.
// The request URL uses 127.0.0.1:8080 deliberately: Burp resolves that
// address on Windows to the Docker-published local Kotlin backend.
private val httpClient = OkHttpClient.Builder()
    .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("10.0.2.2", 8081)))
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

private fun JSONObject.optionalString(name: String): String? = if (isNull(name)) null else optString(name)

data class DemoCustomer(
    val id: Long, val username: String, val fullName: String, val email: String,
    val phone: String, val role: String
)
data class DemoTicket(val id: Long, val userId: Long?, val subject: String, val message: String, val createdAt: String)
data class DemoAccount(val id: Long, val userId: Long, val iban: String, val accountType: String, val balance: Double)
// The username is the bank handle shown to a recipient (e.g. @bob).
// accountId remains the unique account identifier used in the API and Burp requests.
data class DemoRecipient(val accountId: Long, val userId: Long, val username: String, val fullName: String, val accountType: String)
data class DemoTransaction(val id: Long, val accountId: Long, val description: String, val amount: Double, val createdAt: String)
data class TransferConfirmation(
    val transferId: Long, val fromAccountId: Long, val toAccountId: Long,
    val amount: Double, val message: String, val fromUsername: String,
    val toUsername: String, val fromFullName: String, val toFullName: String
)
data class LabLogin(val message: String, val token: String?, val bypassDetected: Boolean, val users: List<DemoCustomer>)

private fun JSONObject.customer() = DemoCustomer(
    getLong("id"),getString("username"),getString("fullName"),getString("email"),
    getString("phone"),getString("role")
)
private fun JSONArray.customers(): List<DemoCustomer> = (0 until length()).map { getJSONObject(it).customer() }

class BankApi {
    /** "X-API-Key" is deliberately extracted from a recoverable Kotlin class. */
    private suspend fun request(path: String, body: JSONObject? = null, needsApiKey: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(BuildConfig.API_BASE_URL + path)
            if (needsApiKey) builder.header("X-API-Key", LabSecrets().getLabApiKey())
            if (body == null) builder.get() else builder.post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            httpClient.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // Production-style login UX: do not show API status or response JSON to the user.
                    // Burp still receives the actual HTTP 401 for security testing.
                    if (path == "/api/v1/auth/login" && response.code == 401) {
                        throw IOException("Wrong username or password")
                    }
                    val hint = if (response.code == 404 && path == "/api/v1/recipients")
                        "Recipient endpoint is missing on the running server. Rebuild the Kotlin backend: docker compose up -d --build --force-recreate server"
                    else if (text.trimStart().startsWith("<html", ignoreCase = true))
                        "Burp returned an HTML page. Verify the proxy listener on 8081 has no redirect and the backend is on 8080."
                    else text.take(260)
                    throw IOException("HTTP ${response.code} ($path): $hint")
                }
                if (text.trimStart().startsWith("<html", ignoreCase = true)) {
                    throw IOException("Expected API data, but Burp returned HTML for $path. Check the 8081 proxy listener and 8080 backend.")
                }
                text
            }
        }

    suspend fun health(): String = request("/health")
    suspend fun login(username: String, password: String): LabLogin {
        val response = JSONObject(request("/api/v1/auth/login", JSONObject().put("username",username).put("password", password)))
        return LabLogin(
            message = response.getString("message"),
            token = response.optionalString("sessionToken"),
            bypassDetected = response.optBoolean("bypassDetected",false),
            users = response.getJSONArray("matchedUsers").customers()
        )
    }
    suspend fun customers(): List<DemoCustomer> = JSONArray(request("/api/v1/customers", needsApiKey=true)).customers()
    suspend fun recipients(): List<DemoRecipient> {
        val array = JSONArray(request("/api/v1/recipients", needsApiKey = true))
        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            DemoRecipient(
                accountId = obj.getLong("accountId"),
                userId = obj.getLong("userId"),
                username = obj.getString("username"),
                fullName = obj.getString("fullName"),
                accountType = obj.getString("accountType")
            )
        }
    }
    suspend fun account(accountId: Long): String = request("/api/v1/accounts/$accountId", needsApiKey=true)
    suspend fun accountDetails(accountId: Long): DemoAccount {
        val obj = JSONObject(account(accountId))
        return DemoAccount(obj.getLong("id"), obj.getLong("userId"), obj.getString("iban"),obj.getString("accountType"),obj.getDouble("balance"))
    }
    suspend fun transactionDetails(accountId: Long): List<DemoTransaction> {
        val array = JSONArray(transactions(accountId))
        return (0 until array.length()).map { idx ->
            val obj = array.getJSONObject(idx)
            DemoTransaction(obj.getLong("id"),obj.getLong("accountId"),obj.getString("description"),obj.getDouble("amount"),obj.getString("createdAt"))
        }
    }
    suspend fun transfer(
        fromAccountId: Long, toAccountId: Long, amount: Double, note: String,
        fromUsername: String? = null, toUsername: String? = null
    ): TransferConfirmation {
        val data = JSONObject()
            .put("fromAccountId", fromAccountId)
            .put("toAccountId", toAccountId)
            .put("amount", amount)
            .put("note", note)
            // Display handles travel with IDs so Burp shows both forms. The server
            // deliberately resolves transfer ownership by IDs (BOLA lab).
            .put("fromUsername", fromUsername ?: JSONObject.NULL)
            .put("toUsername", toUsername ?: JSONObject.NULL)
        val obj = JSONObject(request("/api/v1/transfers", data, needsApiKey = true))
        return TransferConfirmation(
            transferId = obj.getLong("transferId"),
            fromAccountId = obj.getLong("fromAccountId"),
            toAccountId = obj.getLong("toAccountId"),
            amount = obj.getDouble("amount"),
            message = obj.getString("message"),
            fromUsername = obj.optString("fromUsername"),
            toUsername = obj.optString("toUsername"),
            fromFullName = obj.optString("fromFullName"),
            toFullName = obj.optString("toFullName")
        )
    }
    suspend fun transactions(accountId: Long): String = request("/api/v1/transactions?account_id=$accountId", needsApiKey=true)
    suspend fun submitTicket(userId: Long?, subject: String, message: String): Long {
        val body = JSONObject().put("subject",subject).put("message",message).put("userId",userId ?: JSONObject.NULL)
        return JSONObject(request("/api/v1/support",body)).getLong("ticketId")
    }
    suspend fun tickets(): List<DemoTicket> {
        val json = JSONArray(request("/api/v1/support"))
        return (0 until json.length()).map { i ->
            val x = json.getJSONObject(i)
            DemoTicket(x.getLong("id"),if(x.isNull("userId")) null else x.getLong("userId"),
                x.getString("subject"),x.getString("message"),x.getString("createdAt"))
        }
    }
}
