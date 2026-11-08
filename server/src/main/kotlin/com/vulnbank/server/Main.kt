package com.vulnbank.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.sql.ResultSet
import java.util.UUID
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap

/**
 * INTENTIONALLY VULNERABLE BANKING SIMULATION.
 * Local-only by Docker port binding. Every account and credential is fabricated.
 * Never deploy to real users or connect to production systems.
 */
private val labKey = "VULNBANK_LAB_KEY_123"
private val sessions = ConcurrentHashMap<String, Long>()

@Serializable data class LoginRequest(val username: String, val password: String)
@Serializable data class Customer(val id: Long, val username: String, val fullName: String, val email: String, val phone: String, val role: String)
@Serializable data class LoginResponse(
    val authenticated: Boolean,
    val message: String,
    val sessionToken: String? = null,
    val bypassDetected: Boolean = false,
    val matchedUsers: List<Customer> = emptyList()
)
@Serializable data class Account(val id: Long, val userId: Long, val iban: String, val accountType: String, val balance: Double)
@Serializable data class Recipient(val accountId: Long, val userId: Long, val username: String, val fullName: String, val accountType: String)
@Serializable data class Transaction(val id: Long, val accountId: Long, val description: String, val amount: Double, val createdAt: String)
@Serializable data class TransferRequest(
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: Double,
    val note: String = "",
    // UI-facing handles only; never treated as account identifiers.
    val fromUsername: String? = null,
    val toUsername: String? = null
)
@Serializable data class TransferCreated(
    val success: Boolean,
    val transferId: Long,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: Double,
    val message: String,
    val fromUsername: String = "",
    val toUsername: String = "",
    val fromFullName: String = "",
    val toFullName: String = ""
)
@Serializable data class TicketRequest(val userId: Long?, val subject: String, val message: String)
@Serializable data class Ticket(val id: Long, val userId: Long?, val subject: String, val message: String, val createdAt: String)
@Serializable data class TicketCreated(val success: Boolean, val ticketId: Long)
@Serializable data class ApiError(val error: String)
@Serializable data class Health(val status: String, val labMode: Boolean, val warning: String, val backendVersion: String)

private fun ResultSet.customer(): Customer = Customer(
    getLong("id"), getString("username"), getString("full_name"), getString("email"),
    getString("phone"), getString("role")
)
private fun ResultSet.account(): Account = Account(
    getLong("id"), getLong("user_id"), getString("iban"),
    getString("account_type"), getDouble("balance")
)
private fun ResultSet.ticket(): Ticket = Ticket(
    getLong("id"), getLong("user_id").let { if (wasNull()) null else it },
    getString("subject"), getString("message"), getString("created_at")
)

private fun db(): HikariDataSource {
    val cfg = HikariConfig().apply {
        jdbcUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/vulnbank"
        username = System.getenv("DB_USER") ?: "vulnbank"
        password = System.getenv("DB_PASSWORD") ?: "vulnbank_lab"
        maximumPoolSize = 6
        connectionTimeout = 15000
        poolName = "VulnBankLab"
    }
    return HikariDataSource(cfg)
}

fun main() {
    val keyPath = File(System.getenv("CERT_PATH") ?: "server/certs/server.p12")
    val password = System.getenv("CERT_PASSWORD") ?: "lab-changeit"
    require(keyPath.exists()) { "Missing demo TLS certificate: $keyPath" }
    val keyStore = KeyStore.getInstance("PKCS12").apply {
        FileInputStream(keyPath).use { load(it, password.toCharArray()) }
    }
    embeddedServer(
        Netty,
        applicationEnvironment { log = LoggerFactory.getLogger("VulnBank") },
        {
            // Local HTTP listener for Burp interception without CA certificates or Frida.
            // Docker publishes this listener on Windows loopback only.
            connector {
                host = "0.0.0.0"
                port = 8080
            }
            sslConnector(
                keyStore = keyStore,
                keyAlias = "vulnbank",
                keyStorePassword = { password.toCharArray() },
                privateKeyPassword = { password.toCharArray() }
            ) {
                host = "0.0.0.0" // inside container only; host publishes 127.0.0.1:8443
                port = 8443
                keyStorePath = keyPath
            }
        },
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    val dataSource = db()
    // This runs for existing Docker PostgreSQL volumes too. No database reset required.
    dataSource.connection.use { conn ->
        conn.createStatement().use { statement ->
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS bank_transfers (
                    id BIGSERIAL PRIMARY KEY,
                    from_account_id BIGINT NOT NULL REFERENCES accounts(id),
                    to_account_id BIGINT NOT NULL REFERENCES accounts(id),
                    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
                    note VARCHAR(140) NOT NULL DEFAULT '',
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            """.trimIndent())
        }
    }
    environment.monitor.subscribe(ApplicationStopped) { dataSource.close() }
    install(ContentNegotiation) { json(Json { prettyPrint = true; ignoreUnknownKeys = true }) }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            this@module.environment.log.error("VulnBank Lab request failed", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("Lab backend error: check server logs"))
        }
    }
    routing {
        get("/health") { call.respond(Health("ok", true, "LOCAL TRAINING ONLY - ALL DATA SYNTHETIC", "2026.11-recipient-transfer-fix")) }
        route("/api/v1") {
            post("/auth/login") {
                val request = call.receive<LoginRequest>()
                if (request.username.length > 100 || request.password.length > 100) {
                    call.respond(HttpStatusCode.BadRequest, ApiError("Input exceeds lab limit"))
                    return@post
                }
                // VULNERABILITY: user input concatenated directly into SQL.
                // Payload in user-name: ' OR 1=1 --
                val rawSql = "SELECT id,username,full_name,email,phone,role FROM users WHERE username = '${request.username}' AND password = '${request.password}'"
                val matched = dataSource.connection.use { conn ->
                    conn.createStatement().use { statement ->
                        statement.executeQuery(rawSql).use { result ->
                            buildList { while (result.next()) add(result.customer()) }
                        }
                    }
                }
                if (matched.isEmpty()) {
                    call.respond(HttpStatusCode.Unauthorized, LoginResponse(false, "Wrong username or password"))
                } else {
                    val token = "LAB_SESSION_${UUID.randomUUID()}"
                    sessions[token] = matched.first().id
                    call.respond(LoginResponse(
                        authenticated = true,
                        message = if (matched.size > 1) "SQLi lab: authentication bypass, multiple synthetic records returned" else "Login successful",
                        sessionToken = token,
                        bypassDetected = matched.size > 1,
                        matchedUsers = matched
                    ))
                }
            }
            get("/customers") {
                if (!call.authorizedForLabKey(dataSource)) return@get
                val customers = dataSource.connection.use { conn ->
                    conn.prepareStatement("SELECT id,username,full_name,email,phone,role FROM users ORDER BY id").use { statement ->
                        statement.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.customer()) } }
                    }
                }
                call.respond(customers)
            }
            // Recipient directory for the fictional banking users.
            // Handles identify people, and account IDs identify the actual accounts.
            get("/recipients") {
                if (!call.authorizedForLabKey(dataSource)) return@get
                val recipients = dataSource.connection.use { conn ->
                    conn.prepareStatement("""
                        SELECT a.id AS account_id, a.user_id, u.username, u.full_name, a.account_type
                        FROM accounts a JOIN users u ON u.id = a.user_id
                        ORDER BY u.full_name, a.id LIMIT 100
                    """.trimIndent()).use { ps ->
                        ps.executeQuery().use { rs ->
                            buildList { while (rs.next()) add(Recipient(
                                rs.getLong("account_id"), rs.getLong("user_id"),
                                rs.getString("username"), rs.getString("full_name"),
                                rs.getString("account_type")
                            )) }
                        }
                    }
                }
                call.respond(recipients)
            }
            get("/accounts/{id}") {
                if (!call.authorizedForLabKey(dataSource)) return@get
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) { call.respond(HttpStatusCode.BadRequest, ApiError("Invalid account id")); return@get }
                // VULNERABILITY: no ownership/authorization checks -> BOLA / IDOR.
                val account = dataSource.connection.use { conn ->
                    conn.prepareStatement("SELECT id,user_id,iban,account_type,balance FROM accounts WHERE id=?").use { statement ->
                        statement.setLong(1, id)
                        statement.executeQuery().use { rs -> if (rs.next()) rs.account() else null }
                    }
                }
                if (account != null) call.respond(account)
                else call.respond(HttpStatusCode.NotFound, ApiError("No such demo account"))
            }
            get("/transactions") {
                if (!call.authorizedForLabKey(dataSource)) return@get
                val accountId = call.request.queryParameters["account_id"]?.toLongOrNull()
                if (accountId == null) { call.respond(HttpStatusCode.BadRequest, ApiError("Provide account_id")); return@get }
                // VULNERABILITY: same BOLA; account id alone is enough to retrieve transactions.
                val rows = dataSource.connection.use { conn ->
                    conn.prepareStatement("SELECT id,account_id,description,amount,created_at::text AS created_at FROM transactions WHERE account_id=? ORDER BY id DESC").use { statement ->
                        statement.setLong(1,accountId)
                        statement.executeQuery().use { rs ->
                            buildList { while (rs.next()) add(Transaction(rs.getLong("id"), rs.getLong("account_id"), rs.getString("description"), rs.getDouble("amount"), rs.getString("created_at"))) }
                        }
                    }
                }
                call.respond(rows)
            }
            post("/transfers") {
                if (!call.authorizedForLabKey(dataSource)) return@post
                val req = call.receive<TransferRequest>()
                if (req.fromAccountId <= 0L || req.toAccountId <= 0L ||
                    req.fromAccountId == req.toAccountId || !req.amount.isFinite() ||
                    req.amount <= 0.0 || req.amount > 2000.0 || req.note.length > 140) {
                    call.respond(HttpStatusCode.BadRequest, ApiError("Invalid transfer details or demo amount (max 2000)"))
                    return@post
                }
                val money = try {
                    BigDecimal.valueOf(req.amount).setScale(2, RoundingMode.UNNECESSARY)
                } catch (_: ArithmeticException) {
                    call.respond(HttpStatusCode.BadRequest, ApiError("Amount must have no more than two decimal places"))
                    return@post
                }
                // INTENTIONAL BOLA / IDOR for the LOCAL LAB:
                // We accept the caller-supplied fromAccountId without checking that the
                // authenticated user owns it. Intercept POST in Burp and swap this ID.
                // All accounts here are fictional and Docker binds ports to loopback.
                var transferId: Long? = null
                var failure: Pair<HttpStatusCode, String>? = null
                val transferParties = mutableMapOf<Long, Pair<String, String>>()
                dataSource.connection.use { conn ->
                    conn.autoCommit = false
                    try {
                        val balances = mutableMapOf<Long, BigDecimal>()
                        conn.prepareStatement("SELECT id, balance FROM accounts WHERE id IN (?, ?) ORDER BY id FOR UPDATE").use { ps ->
                            ps.setLong(1, req.fromAccountId)
                            ps.setLong(2, req.toAccountId)
                            ps.executeQuery().use { rs ->
                                while (rs.next()) balances[rs.getLong("id")] = rs.getBigDecimal("balance")
                            }
                        }
                        conn.prepareStatement("""
                            SELECT a.id, u.username, u.full_name
                            FROM accounts a JOIN users u ON u.id = a.user_id
                            WHERE a.id IN (?, ?)
                        """.trimIndent()).use { ps ->
                            ps.setLong(1, req.fromAccountId)
                            ps.setLong(2, req.toAccountId)
                            ps.executeQuery().use { rs ->
                                while (rs.next()) transferParties[rs.getLong("id")] =
                                    rs.getString("username") to rs.getString("full_name")
                            }
                        }
                        val sourceBalance = balances[req.fromAccountId]
                        if (sourceBalance == null || !balances.containsKey(req.toAccountId)) {
                            failure = HttpStatusCode.NotFound to "Demo account not found"
                            conn.rollback()
                        } else if (sourceBalance < money) {
                            failure = HttpStatusCode.Conflict to "Insufficient demo balance"
                            conn.rollback()
                        } else {
                            conn.prepareStatement("UPDATE accounts SET balance = balance - ? WHERE id = ?").use { ps ->
                                ps.setBigDecimal(1, money); ps.setLong(2, req.fromAccountId); ps.executeUpdate()
                            }
                            conn.prepareStatement("UPDATE accounts SET balance = balance + ? WHERE id = ?").use { ps ->
                                ps.setBigDecimal(1, money); ps.setLong(2, req.toAccountId); ps.executeUpdate()
                            }
                            conn.prepareStatement("INSERT INTO bank_transfers (from_account_id, to_account_id, amount, note) VALUES (?, ?, ?, ?) RETURNING id").use { ps ->
                                ps.setLong(1, req.fromAccountId)
                                ps.setLong(2, req.toAccountId)
                                ps.setBigDecimal(3, money)
                                ps.setString(4, req.note)
                                ps.executeQuery().use { rs -> rs.next(); transferId = rs.getLong(1) }
                            }
                            val toHandle = transferParties[req.toAccountId]?.first ?: "unknown"
                            val fromHandle = transferParties[req.fromAccountId]?.first ?: "unknown"
                            listOf(
                                Triple(req.fromAccountId, "Transfer to @$toHandle (#${req.toAccountId})", money.negate()),
                                Triple(req.toAccountId, "Transfer from @$fromHandle (#${req.fromAccountId})", money)
                            ).forEach { (accountId, description, signedAmount) ->
                                conn.prepareStatement("INSERT INTO transactions (account_id, description, amount) VALUES (?, ?, ?)").use { ps ->
                                    ps.setLong(1, accountId)
                                    ps.setString(2, description)
                                    ps.setBigDecimal(3, signedAmount)
                                    ps.executeUpdate()
                                }
                            }
                            conn.commit()
                        }
                    } catch (e: Exception) {
                        conn.rollback()
                        throw e
                    } finally {
                        conn.autoCommit = true
                    }
                }
                val rejected = failure
                if (rejected != null) call.respond(rejected.first, ApiError(rejected.second))
                else call.respond(HttpStatusCode.Created, TransferCreated(
                    true, requireNotNull(transferId), req.fromAccountId, req.toAccountId,
                    money.toDouble(), "Transfer completed",
                    fromUsername = transferParties[req.fromAccountId]?.first.orEmpty(),
                    toUsername = transferParties[req.toAccountId]?.first.orEmpty(),
                    fromFullName = transferParties[req.fromAccountId]?.second.orEmpty(),
                    toFullName = transferParties[req.toAccountId]?.second.orEmpty()
                ))
            }
            post("/support") {
                val req = call.receive<TicketRequest>()
                if (req.subject.isBlank() || req.subject.length > 160 || req.message.isBlank() || req.message.length > 5000) {
                    call.respond(HttpStatusCode.BadRequest, ApiError("Subject 1-160; message 1-5000 characters"))
                    return@post
                }
                val id = dataSource.connection.use { conn ->
                    conn.prepareStatement("INSERT INTO support_tickets (user_id,subject,message) VALUES (?,?,?) RETURNING id").use { ps ->
                        if (req.userId == null) ps.setNull(1, java.sql.Types.BIGINT) else ps.setLong(1, req.userId)
                        ps.setString(2, req.subject)
                        ps.setString(3, req.message) // stored verbatim; XSS occurs in vulnerable WebView renderer
                        ps.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
                    }
                }
                call.respond(HttpStatusCode.Created, TicketCreated(true, id))
            }
            get("/support") {
                // No auth: intentionally permissive local-only lab; data is synthetic.
                val rows = dataSource.connection.use { conn ->
                    conn.prepareStatement("SELECT id,user_id,subject,message,created_at::text AS created_at FROM support_tickets ORDER BY id DESC LIMIT 100").use { statement ->
                        statement.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.ticket()) } }
                    }
                }
                call.respond(rows)
            }
            get("/support/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) { call.respond(HttpStatusCode.BadRequest, ApiError("Invalid ticket id")); return@get }
                val ticket = dataSource.connection.use { conn ->
                    conn.prepareStatement("SELECT id,user_id,subject,message,created_at::text AS created_at FROM support_tickets WHERE id=?").use { statement ->
                        statement.setLong(1,id)
                        statement.executeQuery().use { rs -> if(rs.next()) rs.ticket() else null }
                    }
                }
                if (ticket != null) call.respond(ticket)
                else call.respond(HttpStatusCode.NotFound, ApiError("No such demo ticket"))
            }
        }
    }
}

private suspend fun ApplicationCall.authorizedForLabKey(db: HikariDataSource): Boolean {
    val key = request.headers["X-API-Key"]
    val allowed = if (key == null || key.length > 180) false else db.connection.use { conn ->
        conn.prepareStatement("SELECT 1 FROM api_keys WHERE api_key=? AND active=TRUE").use { ps ->
            ps.setString(1,key)
            ps.executeQuery().use { it.next() }
        }
    }
    if (!allowed) respond(HttpStatusCode.Unauthorized, ApiError("Valid X-API-Key required"))
    return allowed
}
