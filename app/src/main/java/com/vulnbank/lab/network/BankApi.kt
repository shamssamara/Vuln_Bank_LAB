
package com.vulnbank.lab.network

import com.vulnbank.lab.BuildConfig
import com.vulnbank.lab.security.LabSecrets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.UUID
import java.util.concurrent.TimeUnit

/*
 * VulnBank LAB - Dual Mode Burp Configuration
 *
 * LOCAL TRAINING ONLY
 *
 * Vulnerable:
 *   Android -> Burp 10.0.2.2:8081
 *   Burp -> Ktor 127.0.0.1:8080
 *
 * Secure:
 *   Android -> Burp 10.0.2.2:8083
 *   Burp -> Ktor 127.0.0.1:8082
 *
 * Both modes use explicit OkHttp proxies.
 * No emulator-wide proxy configuration is required.
 *
 * HTTP interception is intentional for this laboratory.
 * Secure Mode demonstrates server-side hardening;
 * it does not provide encrypted transport security.
 */

private const val BURP_HOST = "10.0.2.2"
private const val VULNERABLE_PROXY_PORT = 8081
private const val SECURE_PROXY_PORT = 8083

private const val SECURE_BACKEND_URL =
    "http://127.0.0.1:8082"

private fun createBurpClient(port: Int): OkHttpClient {
    val proxy = Proxy(
        Proxy.Type.HTTP,
        InetSocketAddress(BURP_HOST, port)
    )

    return OkHttpClient.Builder()
        .proxy(proxy)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

private val vulnerableClient by lazy {
    createBurpClient(VULNERABLE_PROXY_PORT)
}

private val secureClient by lazy {
    createBurpClient(SECURE_PROXY_PORT)
}

enum class LabMode(
    val wireValue: String,
    val title: String
) {
    VULNERABLE("vulnerable", "Vulnerable"),
    SECURE("secure", "Secure")
}

private fun JSONObject.optionalString(name: String): String? =
    if (isNull(name)) null else optString(name)

data class DemoCustomer(
    val id: Long,
    val username: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val role: String
)

data class DemoTicket(
    val id: Long,
    val userId: Long?,
    val subject: String,
    val message: String,
    val createdAt: String
)

data class DemoAccount(
    val id: Long,
    val userId: Long,
    val iban: String,
    val accountType: String,
    val balance: Double
)

data class DemoRecipient(
    val accountId: Long,
    val userId: Long,
    val username: String,
    val fullName: String,
    val accountType: String
)

data class DemoTransaction(
    val id: Long,
    val accountId: Long,
    val description: String,
    val amount: Double,
    val createdAt: String
)

data class TransferConfirmation(
    val transferId: Long,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: Double,
    val message: String,
    val replayed: Boolean,
    val fromUsername: String,
    val toUsername: String,
    val fromFullName: String,
    val toFullName: String
)

data class LabLogin(
    val message: String,
    val token: String?,
    val bypassDetected: Boolean,
    val users: List<DemoCustomer>
)

private fun JSONObject.customer() = DemoCustomer(
    getLong("id"),
    getString("username"),
    getString("fullName"),
    getString("email"),
    getString("phone"),
    getString("role")
)

private fun JSONArray.customers(): List<DemoCustomer> =
    (0 until length()).map {
        getJSONObject(it).customer()
    }

class BankApi(
    val mode: LabMode = LabMode.VULNERABLE
) {

    @Volatile
    private var sessionToken: String? = null

    /*
     * Permanent Burp routing for both modes.
     */
    private val client: OkHttpClient =
        if (mode == LabMode.SECURE) {
            secureClient
        } else {
            vulnerableClient
        }

    /*
     * The destination is resolved by Burp on Windows.
     *
     * 127.0.0.1 is intentional for the Secure backend,
     * because the HTTP proxy connects to that address
     * from the Windows host.
     */
    private val baseUrl: String =
        if (mode == LabMode.SECURE) {
            SECURE_BACKEND_URL
        } else {
            BuildConfig.API_BASE_URL
        }

    fun setSession(token: String?) {
        sessionToken = token
    }

    fun clearSession() {
        sessionToken = null
    }

    private suspend fun request(
        path: String,
        body: JSONObject? = null,
        needsApiKey: Boolean = false,
        idempotencyKey: String? = null
    ): String = withContext(Dispatchers.IO) {

        val builder = Request.Builder()
            .url(baseUrl + path)

        if (needsApiKey && mode == LabMode.VULNERABLE) {
            builder.header(
                "X-API-Key",
                LabSecrets().getLabApiKey()
            )
        }

        if (mode == LabMode.SECURE) {
            sessionToken?.let { token ->
                builder.header(
                    "Authorization",
                    "Bearer $token"
                )
            }
        }

        if (idempotencyKey != null) {
            builder.header(
                "Idempotency-Key",
                idempotencyKey
            )
        }

        if (body == null) {
            builder.get()
        } else {
            builder.post(
                body.toString().toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )
            )
        }

        client.newCall(builder.build()).execute().use { response ->

            val text = response.body?.string().orEmpty()

            if (!response.isSuccessful) {

                // Generic login error for both modes.
                if (
                    path == "/api/v1/auth/login" &&
                    response.code == 401
                ) {
                    throw IOException(
                        "Wrong username or password"
                    )
                }

                val hint = when {

                    response.code == 404 &&
                            path == "/api/v1/recipients" -> {
                        "Recipient endpoint is missing on the running server. " +
                                "Rebuild the Kotlin backend."
                    }

                    mode == LabMode.SECURE &&
                            response.code == 401 -> {
                        "Session expired. Please sign in again."
                    }

                    text.trimStart().startsWith(
                        "<html",
                        ignoreCase = true
                    ) -> {
                        val port =
                            if (mode == LabMode.SECURE) 8083 else 8081

                        "Burp returned an HTML page. " +
                                "Check the proxy listener on $port."
                    }

                    else -> text.take(260)
                }

                throw IOException(
                    "HTTP ${response.code} ($path): $hint"
                )
            }

            if (
                text.trimStart().startsWith(
                    "<html",
                    ignoreCase = true
                )
            ) {
                val port =
                    if (mode == LabMode.SECURE) 8083 else 8081

                throw IOException(
                    "Expected API data, but Burp returned HTML " +
                            "for $path. Check listener $port."
                )
            }

            text
        }
    }

    suspend fun health(): String =
        request("/health")

    suspend fun verifyMode(): String {

        val result = JSONObject(health())

        val serverMode = result.optString(
            "securityMode",
            ""
        )

        if (serverMode != mode.wireValue) {
            throw IOException(
                "The selected ${mode.title} backend is " +
                        "not running or is outdated. " +
                        "Run: docker compose up -d --build"
            )
        }

        return serverMode
    }

    /*
     * Secure logout:
     * Clear local session and revoke the token
     * through the same configured Burp proxy.
     */
    fun logoutAsync(scope: CoroutineScope) {

        val toRevoke = sessionToken

        clearSession()

        if (
            mode != LabMode.SECURE ||
            toRevoke == null
        ) {
            return
        }

        scope.launch(Dispatchers.IO) {

            try {

                val req = Request.Builder()
                    .url(baseUrl + "/api/v1/auth/logout")
                    .header(
                        "Authorization",
                        "Bearer $toRevoke"
                    )
                    .post(
                        "{}".toRequestBody(
                            "application/json; charset=utf-8".toMediaType()
                        )
                    )
                    .build()

                client.newCall(req)
                    .execute()
                    .close()

            } catch (_: IOException) {
                // Session also expires server-side.
            }
        }
    }

    suspend fun login(
        username: String,
        password: String
    ): LabLogin {

        val response = JSONObject(
            request(
                "/api/v1/auth/login",
                JSONObject()
                    .put("username", username)
                    .put("password", password)
            )
        )

        return LabLogin(
            message = response.getString("message"),
            token = response.optionalString("sessionToken"),
            bypassDetected = response.optBoolean(
                "bypassDetected",
                false
            ),
            users = response
                .getJSONArray("matchedUsers")
                .customers()
        )
    }

    suspend fun customers(): List<DemoCustomer> =
        JSONArray(
            request(
                "/api/v1/customers",
                needsApiKey = true
            )
        ).customers()

    suspend fun recipients(): List<DemoRecipient> {

        val array = JSONArray(
            request(
                "/api/v1/recipients",
                needsApiKey = true
            )
        )

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

    suspend fun account(
        accountId: Long
    ): String = request(
        "/api/v1/accounts/$accountId",
        needsApiKey = true
    )

    suspend fun accountDetails(
        accountId: Long
    ): DemoAccount {

        val obj = JSONObject(
            account(accountId)
        )

        return DemoAccount(
            id = obj.getLong("id"),
            userId = obj.getLong("userId"),
            iban = obj.getString("iban"),
            accountType = obj.getString("accountType"),
            balance = obj.getDouble("balance")
        )
    }

    suspend fun transactionDetails(
        accountId: Long
    ): List<DemoTransaction> {

        val array = JSONArray(
            transactions(accountId)
        )

        return (0 until array.length()).map { idx ->

            val obj = array.getJSONObject(idx)

            DemoTransaction(
                id = obj.getLong("id"),
                accountId = obj.getLong("accountId"),
                description = obj.getString("description"),
                amount = obj.getDouble("amount"),
                createdAt = obj.getString("createdAt")
            )
        }
    }

    /*
     * Transaction Replay Lab:
     *
     * Vulnerable backend intentionally allows
     * repeated execution.
     *
     * Secure backend uses idempotency protection.
     *
     * This method preserves the Idempotency-Key
     * supplied by the caller.
     */
    suspend fun transfer(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Double,
        note: String,
        fromUsername: String? = null,
        toUsername: String? = null,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): TransferConfirmation {

        val data = JSONObject()
            .put("fromAccountId", fromAccountId)
            .put("toAccountId", toAccountId)
            .put("amount", amount)
            .put("note", note)
            .put(
                "fromUsername",
                fromUsername ?: JSONObject.NULL
            )
            .put(
                "toUsername",
                toUsername ?: JSONObject.NULL
            )

        val obj = JSONObject(
            request(
                "/api/v1/transfers",
                body = data,
                needsApiKey = true,
                idempotencyKey = idempotencyKey
            )
        )

        return TransferConfirmation(
            transferId = obj.getLong("transferId"),
            fromAccountId = obj.getLong("fromAccountId"),
            toAccountId = obj.getLong("toAccountId"),
            amount = obj.getDouble("amount"),
            message = obj.getString("message"),
            replayed = obj.optBoolean("replayed", false),
            fromUsername = obj.optString("fromUsername"),
            toUsername = obj.optString("toUsername"),
            fromFullName = obj.optString("fromFullName"),
            toFullName = obj.optString("toFullName")
        )
    }

    suspend fun transactions(
        accountId: Long
    ): String = request(
        "/api/v1/transactions?account_id=$accountId",
        needsApiKey = true
    )

    suspend fun submitTicket(
        userId: Long?,
        subject: String,
        message: String
    ): Long {

        val body = JSONObject()
            .put("subject", subject)
            .put("message", message)
            .put(
                "userId",
                userId ?: JSONObject.NULL
            )

        return JSONObject(
            request(
                "/api/v1/support",
                body
            )
        ).getLong("ticketId")
    }

    suspend fun tickets(): List<DemoTicket> {

        val json = JSONArray(
            request("/api/v1/support")
        )

        return (0 until json.length()).map { i ->

            val x = json.getJSONObject(i)

            DemoTicket(
                id = x.getLong("id"),
                userId = if (x.isNull("userId")) {
                    null
                } else {
                    x.getLong("userId")
                },
                subject = x.getString("subject"),
                message = x.getString("message"),
                createdAt = x.getString("createdAt")
            )
        }
    }
}
