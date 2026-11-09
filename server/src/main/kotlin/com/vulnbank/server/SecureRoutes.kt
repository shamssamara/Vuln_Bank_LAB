package com.vulnbank.server

import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * HARDENED COMPARISON for an isolated educational environment, not production banking security.
 * Runs as a SEPARATE service with a SEPARATE PostgreSQL database. A client-chosen mode
 * is never treated as a security control on the server.
 */
private data class SecureSession(val userId: Long, val expiresAt: Long)
private data class LoginFailures(val attempts: Int, val windowStart: Long)
private val secureSessions = ConcurrentHashMap<String, SecureSession>()
private val failedLogins = ConcurrentHashMap<String, LoginFailures>()
private val sessionRandom = SecureRandom()
private const val SESSION_LIFETIME_MS = 15 * 60 * 1000L
private const val LOGIN_WINDOW_MS = 10 * 60 * 1000L

private fun newSessionToken(): String = ByteArray(32).also(sessionRandom::nextBytes).let {
    Base64.getUrlEncoder().withoutPadding().encodeToString(it)
}

private suspend fun ApplicationCall.secureUser(): Long? {
    val token = request.headers[HttpHeaders.Authorization]
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.substringAfter(' ')?.trim()
    val current = token?.let(secureSessions::get)
    if (current == null || current.expiresAt <= System.currentTimeMillis()) {
        if (token != null) secureSessions.remove(token)
        respond(HttpStatusCode.Unauthorized, ApiError("Authentication required or session expired"))
        return null
    }
    return current.userId
}

private fun secureFingerprint(req: TransferRequest, money: BigDecimal): String {
    val raw = "${req.fromAccountId}|${req.toAccountId}|${money.toPlainString()}|${req.note}"
    return MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

private fun ensureSecureTransferTables(ds: HikariDataSource) {
    ds.connection.use { conn -> conn.createStatement().use { stmt ->
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS bank_transfers (
                id BIGSERIAL PRIMARY KEY,
                from_account_id BIGINT NOT NULL REFERENCES accounts(id),
                to_account_id BIGINT NOT NULL REFERENCES accounts(id),
                amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
                note VARCHAR(140) NOT NULL DEFAULT '',
                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent())
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS secure_transfer_requests (
                user_id BIGINT NOT NULL REFERENCES users(id),
                idempotency_key VARCHAR(128) NOT NULL,
                request_fingerprint CHAR(64) NOT NULL,
                transfer_id BIGINT REFERENCES bank_transfers(id),
                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (user_id, idempotency_key)
            )
        """.trimIndent())
    } }
}

private fun Connection.ownedAccount(userId: Long, accountId: Long): Boolean =
    prepareStatement("SELECT 1 FROM accounts WHERE id=? AND user_id=?").use { ps ->
        ps.setLong(1, accountId)
        ps.setLong(2, userId)
        ps.executeQuery().use { it.next() }
    }

private data class SecureTransferOutcome(val id: Long, val repeated: Boolean, val error: String? = null, val status: HttpStatusCode = HttpStatusCode.OK)

private fun secureTransfer(
    ds: HikariDataSource,
    userId: Long,
    key: String,
    req: TransferRequest,
    amount: BigDecimal
): SecureTransferOutcome {
    val fingerprint = secureFingerprint(req, amount)
    ds.connection.use { conn ->
        conn.autoCommit = false
        try {
            // ON CONFLICT acquires the unique-index conflict lock. A concurrent retry
            // cannot process until the first transaction commits or rolls back.
            val inserted = conn.prepareStatement("""
                INSERT INTO secure_transfer_requests (user_id,idempotency_key,request_fingerprint)
                VALUES (?,?,?) ON CONFLICT DO NOTHING RETURNING user_id
            """.trimIndent()).use { ps ->
                ps.setLong(1, userId); ps.setString(2, key); ps.setString(3, fingerprint)
                ps.executeQuery().use { it.next() }
            }
            if (!inserted) {
                val recorded = conn.prepareStatement("""
                    SELECT request_fingerprint, transfer_id
                    FROM secure_transfer_requests WHERE user_id=? AND idempotency_key=? FOR UPDATE
                """.trimIndent()).use { ps ->
                    ps.setLong(1,userId); ps.setString(2,key)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) Pair(rs.getString(1),rs.getLong(2)) else null
                    }
                }
                conn.commit()
                return if (recorded == null) SecureTransferOutcome(0, false, "Retry request",HttpStatusCode.Conflict)
                else if (recorded.first != fingerprint) SecureTransferOutcome(0,false,"Idempotency-Key already used for different transfer details",HttpStatusCode.Conflict)
                else SecureTransferOutcome(recorded.second,true)
            }
            if (!conn.ownedAccount(userId,req.fromAccountId)) {
                conn.rollback(); return SecureTransferOutcome(0,false,"Source account not owned by authenticated user",HttpStatusCode.Forbidden)
            }
            val balances = mutableMapOf<Long,BigDecimal>()
            conn.prepareStatement("SELECT id,balance FROM accounts WHERE id IN (?,?) ORDER BY id FOR UPDATE").use { ps ->
                ps.setLong(1,req.fromAccountId); ps.setLong(2,req.toAccountId)
                ps.executeQuery().use { rs -> while (rs.next()) balances[rs.getLong(1)] = rs.getBigDecimal(2) }
            }
            if (balances.size != 2) {
                conn.rollback(); return SecureTransferOutcome(0,false,"Account not found",HttpStatusCode.NotFound)
            }
            if (requireNotNull(balances[req.fromAccountId]) < amount) {
                conn.rollback(); return SecureTransferOutcome(0,false,"Insufficient demo balance",HttpStatusCode.Conflict)
            }
            conn.prepareStatement("UPDATE accounts SET balance=balance-? WHERE id=?").use { ps ->
                ps.setBigDecimal(1,amount); ps.setLong(2,req.fromAccountId); ps.executeUpdate()
            }
            conn.prepareStatement("UPDATE accounts SET balance=balance+? WHERE id=?").use { ps ->
                ps.setBigDecimal(1,amount); ps.setLong(2,req.toAccountId); ps.executeUpdate()
            }
            val transferId = conn.prepareStatement("""
                INSERT INTO bank_transfers(from_account_id,to_account_id,amount,note)
                VALUES(?,?,?,?) RETURNING id
            """.trimIndent()).use { ps ->
                ps.setLong(1,req.fromAccountId); ps.setLong(2,req.toAccountId)
                ps.setBigDecimal(3,amount); ps.setString(4,req.note)
                ps.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
            }
            val handles = mutableMapOf<Long,String>()
            conn.prepareStatement("""
                SELECT a.id,u.username FROM accounts a JOIN users u ON u.id=a.user_id WHERE a.id IN (?,?)
            """.trimIndent()).use { ps ->
                ps.setLong(1,req.fromAccountId); ps.setLong(2,req.toAccountId)
                ps.executeQuery().use { rs -> while(rs.next()) handles[rs.getLong(1)] = rs.getString(2) }
            }
            listOf(
                Triple(req.fromAccountId, "Transfer to @${handles[req.toAccountId]} (#${req.toAccountId})",amount.negate()),
                Triple(req.toAccountId, "Transfer from @${handles[req.fromAccountId]} (#${req.fromAccountId})",amount)
            ).forEach { (account, description, signed) ->
                conn.prepareStatement("INSERT INTO transactions(account_id,description,amount) VALUES(?,?,?)").use { ps ->
                    ps.setLong(1,account); ps.setString(2,description); ps.setBigDecimal(3,signed); ps.executeUpdate()
                }
            }
            conn.prepareStatement("""
                UPDATE secure_transfer_requests SET transfer_id=? WHERE user_id=? AND idempotency_key=?
            """.trimIndent()).use { ps ->
                ps.setLong(1,transferId); ps.setLong(2,userId); ps.setString(3,key); ps.executeUpdate()
            }
            conn.commit()
            return SecureTransferOutcome(transferId,false)
        } catch(e:Exception) { conn.rollback(); throw e }
        finally { conn.autoCommit = true }
    }
}

fun Route.secureRoutes(ds: HikariDataSource) {
    ensureSecureTransferTables(ds)
    get("/health") {
        call.respond(Health("ok", true, "HARDENED COMPARISON DEMO - LOCAL ONLY, NOT PRODUCTION SECURITY", "2026.11-dual-mode-replay", "secure"))
    }
    route("/api/v1") {
        post("/auth/login") {
            val body = call.receive<LoginRequest>()
            if (body.username.length !in 1..60 || body.password.length !in 1..100) {
                call.respond(HttpStatusCode.Unauthorized, LoginResponse(false,"Wrong username or password")); return@post
            }
            val now = System.currentTimeMillis()
            val idKey = body.username.lowercase()
            val failure = failedLogins[idKey]
            if (failure != null && now - failure.windowStart < LOGIN_WINDOW_MS && failure.attempts >= 5) {
                call.respond(HttpStatusCode.TooManyRequests, ApiError("Too many attempts. Try again later.")); return@post
            }
            val user = ds.connection.use { conn ->
                // PostgreSQL pgcrypto bcrypt hash verification; no SQL concatenation.
                conn.prepareStatement("""
                    SELECT id,username,full_name,email,phone,role
                    FROM users WHERE username=? AND password=crypt(?, password)
                """.trimIndent()).use { ps ->
                    ps.setString(1,body.username); ps.setString(2,body.password)
                    ps.executeQuery().use { rs -> if(rs.next()) Customer(
                        rs.getLong("id"),rs.getString("username"),rs.getString("full_name"),
                        rs.getString("email"),rs.getString("phone"),rs.getString("role")
                    ) else null }
                }
            }
            if (user == null) {
                failedLogins.compute(idKey) { _,old ->
                    if(old==null || now-old.windowStart>=LOGIN_WINDOW_MS) LoginFailures(1,now)
                    else old.copy(attempts=old.attempts+1)
                }
                call.respond(HttpStatusCode.Unauthorized, LoginResponse(false,"Wrong username or password"))
            } else {
                failedLogins.remove(idKey)
                val token = newSessionToken()
                secureSessions[token] = SecureSession(user.id, now+SESSION_LIFETIME_MS)
                call.respond(LoginResponse(true,"Login successful",token,false,listOf(user)))
            }
        }
        post("/auth/logout") {
            val raw = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
            if(raw!=null) secureSessions.remove(raw)
            call.respond(mapOf("success" to true))
        }
        get("/customers") {
            val userId = call.secureUser() ?: return@get
            // Secure edition does NOT disclose the entire customer database.
            val own = ds.connection.use { conn ->
                conn.prepareStatement("SELECT id,username,full_name,email,phone,role FROM users WHERE id=?").use { ps ->
                    ps.setLong(1,userId)
                    ps.executeQuery().use { rs -> if(rs.next()) listOf(Customer(
                        rs.getLong("id"),rs.getString("username"),rs.getString("full_name"),
                        rs.getString("email"),rs.getString("phone"),rs.getString("role")
                    )) else emptyList() }
                }
            }
            call.respond(own)
        }
        get("/recipients") {
            call.secureUser() ?: return@get
            val values = ds.connection.use { conn ->
                conn.prepareStatement("""
                    SELECT a.id AS account_id,a.user_id,u.username,u.full_name,a.account_type
                    FROM accounts a JOIN users u ON u.id=a.user_id ORDER BY u.full_name,a.id LIMIT 100
                """.trimIndent()).use { ps ->
                    ps.executeQuery().use { rs -> buildList<Recipient> { while(rs.next()) add(Recipient(
                        rs.getLong("account_id"),rs.getLong("user_id"),rs.getString("username"),
                        rs.getString("full_name"),rs.getString("account_type")
                    )) } }
                }
            }
            call.respond(values)
        }
        get("/accounts/{id}") {
            val userId = call.secureUser() ?: return@get
            val id = call.parameters["id"]?.toLongOrNull()
            if(id==null){ call.respond(HttpStatusCode.BadRequest,ApiError("Invalid account id")); return@get }
            val found = ds.connection.use { conn ->
                conn.prepareStatement("SELECT id,user_id,iban,account_type,balance FROM accounts WHERE id=? AND user_id=?").use { ps ->
                    ps.setLong(1,id); ps.setLong(2,userId)
                    ps.executeQuery().use { rs -> if(rs.next()) Account(
                        rs.getLong("id"),rs.getLong("user_id"),rs.getString("iban"),
                        rs.getString("account_type"),rs.getDouble("balance")
                    ) else null }
                }
            }
            if(found==null) call.respond(HttpStatusCode.Forbidden,ApiError("Account access denied")) else call.respond(found)
        }
        get("/transactions") {
            val userId = call.secureUser() ?: return@get
            val account = call.request.queryParameters["account_id"]?.toLongOrNull()
            if(account==null) {call.respond(HttpStatusCode.BadRequest,ApiError("Provide account_id")); return@get}
            val result = ds.connection.use { conn ->
                if(!conn.ownedAccount(userId,account)) null else conn.prepareStatement("""
                    SELECT id,account_id,description,amount,created_at::text AS created_at
                    FROM transactions WHERE account_id=? ORDER BY id DESC LIMIT 200
                """.trimIndent()).use { ps ->
                    ps.setLong(1,account)
                    ps.executeQuery().use { rs -> buildList<Transaction> { while(rs.next()) add(Transaction(
                        rs.getLong("id"),rs.getLong("account_id"),rs.getString("description"),
                        rs.getDouble("amount"),rs.getString("created_at")
                    )) } }
                }
            }
            if(result==null) call.respond(HttpStatusCode.Forbidden,ApiError("Transaction access denied")) else call.respond(result)
        }
        post("/transfers") {
            val userId = call.secureUser() ?: return@post
            val key = call.request.headers["Idempotency-Key"]
            if(key==null || !key.matches(Regex("[A-Za-z0-9._:-]{8,128}"))) {
                call.respond(HttpStatusCode.BadRequest,ApiError("A valid Idempotency-Key is required")); return@post
            }
            val req = call.receive<TransferRequest>()
            val money = try { BigDecimal.valueOf(req.amount).setScale(2,RoundingMode.UNNECESSARY) }
                        catch(_:Exception) { null }
            if(req.fromAccountId<=0 || req.toAccountId<=0 || req.fromAccountId==req.toAccountId ||
                !req.amount.isFinite() || req.amount<=0 || req.amount>2000 || req.note.length>140 || money==null) {
                call.respond(HttpStatusCode.BadRequest,ApiError("Invalid transfer details")); return@post
            }
            val outcome = secureTransfer(ds,userId,key,req,money)
            if(outcome.error!=null) {call.respond(outcome.status,ApiError(outcome.error)); return@post}
            val names = ds.connection.use { conn ->
                conn.prepareStatement("""
                    SELECT a.id,u.username,u.full_name FROM accounts a
                    JOIN users u ON u.id=a.user_id WHERE a.id IN (?,?)
                """.trimIndent()).use { ps ->
                    ps.setLong(1,req.fromAccountId);ps.setLong(2,req.toAccountId)
                    ps.executeQuery().use { rs -> buildMap<Long, Pair<String, String>> {while(rs.next()) put(rs.getLong(1),rs.getString(2) to rs.getString(3))} }
                }
            }
            call.respond(if(outcome.repeated) HttpStatusCode.OK else HttpStatusCode.Created, TransferCreated(
                true,outcome.id,req.fromAccountId,req.toAccountId,money.toDouble(),
                if(outcome.repeated) "Already processed — original transfer returned" else "Transfer completed",
                names[req.fromAccountId]?.first.orEmpty(),names[req.toAccountId]?.first.orEmpty(),
                names[req.fromAccountId]?.second.orEmpty(),names[req.toAccountId]?.second.orEmpty(),
                replayed=outcome.repeated
            ))
        }
        post("/support") {
            val userId = call.secureUser() ?: return@post
            val req = call.receive<TicketRequest>()
            if(req.subject.isBlank() || req.subject.length>160 || req.message.isBlank() || req.message.length>5000) {
                call.respond(HttpStatusCode.BadRequest,ApiError("Invalid support ticket")); return@post
            }
            val ticketId = ds.connection.use { conn ->
                conn.prepareStatement("INSERT INTO support_tickets(user_id,subject,message) VALUES(?,?,?) RETURNING id").use { ps ->
                    ps.setLong(1,userId);ps.setString(2,req.subject);ps.setString(3,req.message)
                    ps.executeQuery().use { rs -> rs.next();rs.getLong(1) }
                }
            }
            call.respond(HttpStatusCode.Created,TicketCreated(true,ticketId))
        }
        get("/support") {
            val userId=call.secureUser() ?: return@get
            val tickets = ds.connection.use { conn ->
                conn.prepareStatement("""
                    SELECT id,user_id,subject,message,created_at::text AS created_at
                    FROM support_tickets WHERE user_id=? ORDER BY id DESC LIMIT 100
                """.trimIndent()).use { ps ->
                    ps.setLong(1,userId)
                    ps.executeQuery().use { rs -> buildList<Ticket> { while(rs.next()) add(Ticket(
                        rs.getLong("id"),rs.getLong("user_id"),rs.getString("subject"),
                        rs.getString("message"),rs.getString("created_at")
                    )) } }
                }
            }
            call.respond(tickets)
        }
        get("/support/{id}") {
            val userId=call.secureUser() ?: return@get
            val id=call.parameters["id"]?.toLongOrNull()
            if(id==null) {call.respond(HttpStatusCode.BadRequest,ApiError("Invalid ticket id"));return@get}
            val found=ds.connection.use { conn ->
                conn.prepareStatement("""
                    SELECT id,user_id,subject,message,created_at::text AS created_at
                    FROM support_tickets WHERE id=? AND user_id=?
                """.trimIndent()).use { ps ->
                    ps.setLong(1,id);ps.setLong(2,userId)
                    ps.executeQuery().use { rs -> if(rs.next()) Ticket(
                        rs.getLong("id"),rs.getLong("user_id"),rs.getString("subject"),
                        rs.getString("message"),rs.getString("created_at")
                    ) else null }
                }
            }
            if(found==null)call.respond(HttpStatusCode.Forbidden,ApiError("Ticket access denied")) else call.respond(found)
        }
    }
}
