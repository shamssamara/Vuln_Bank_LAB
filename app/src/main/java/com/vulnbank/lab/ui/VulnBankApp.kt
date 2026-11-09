package com.vulnbank.lab.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vulnbank.lab.network.BankApi
import com.vulnbank.lab.network.LabMode
import java.util.UUID
import com.vulnbank.lab.network.DemoCustomer
import com.vulnbank.lab.network.DemoTicket
import com.vulnbank.lab.network.DemoAccount
import com.vulnbank.lab.network.DemoRecipient
import com.vulnbank.lab.network.DemoTransaction
import com.vulnbank.lab.security.AuthGate
import com.vulnbank.lab.security.LabSecrets
import com.vulnbank.lab.security.RootGate
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon

private val tabs = listOf("Home", "Transfer", "Support", "API Lab", "More")

@Composable fun VulnBankApp() {
    BankTheme {
        val context = LocalContext.current
        var selectedMode by remember { mutableStateOf<LabMode?>(null) }
        val api = remember(selectedMode) { BankApi(selectedMode ?: LabMode.VULNERABLE) }
        val scope = rememberCoroutineScope()
        val prefs = remember { context.getSharedPreferences("vulnbank_lab",Context.MODE_PRIVATE) }
        var page by remember { mutableStateOf("ModeSelect") }
        var username by rememberSaveable { mutableStateOf("") }
        var password by rememberSaveable { mutableStateOf("") }
        var sessionUser by remember { mutableStateOf<DemoCustomer?>(null) }
        var sessionMatches by remember { mutableStateOf<List<DemoCustomer>>(emptyList()) }
        var bypass by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var busyLabel by remember { mutableStateOf("Processing your request") }
        var showSplash by remember { mutableStateOf(true) }
        var navigating by remember { mutableStateOf(false) }
        var navigationInitialized by remember { mutableStateOf(false) }
        LaunchedEffect(page) {
            if (navigationInitialized) {
                navigating = true
                delay(320) // Brief branded transition when moving between tabs.
                navigating = false
            } else {
                navigationInitialized = true
            }
        }
        LaunchedEffect(Unit) {
            delay(1150) // Brand moment only at launch; never delay a real network request.
            showSplash = false
        }
        var notice by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        var customers by remember { mutableStateOf<List<DemoCustomer>>(emptyList()) }
        var myAccount by remember { mutableStateOf<DemoAccount?>(null) }
        var recentActivity by remember { mutableStateOf<List<DemoTransaction>>(emptyList()) }
        var recipientSearch by rememberSaveable { mutableStateOf("") }
        var selectedRecipient by remember { mutableStateOf<DemoRecipient?>(null) }
        var recipients by remember { mutableStateOf<List<DemoRecipient>>(emptyList()) }
        var transferFeedback by remember { mutableStateOf("") }
        var transferFeedbackIsError by remember { mutableStateOf(false) }
        var transferAmount by rememberSaveable { mutableStateOf("15.00") }
        var transferNote by rememberSaveable { mutableStateOf("") }
        var apiOutput by remember { mutableStateOf("Select a lab API request to view its response.") }
        var replayResult by remember { mutableStateOf("Choose Run Replay Test to send the same transfer request twice.") }
        var accountId by rememberSaveable { mutableStateOf("2") }
        var subject by rememberSaveable { mutableStateOf("Account assistance") }
        var ticketMessage by rememberSaveable { mutableStateOf("") }
        var tickets by remember { mutableStateOf<List<DemoTicket>>(emptyList()) }
        var viewingTicket by remember { mutableStateOf<DemoTicket?>(null) }
        var rootSimulated by remember { mutableStateOf(prefs.getBoolean("simulate_root",false)) }
        var rootResult by remember { mutableStateOf<String?>(null) }
        var authResult by remember { mutableStateOf<String?>(null) }
        fun run(label: String = "Loading your account", block: suspend () -> Unit) {
            scope.launch {
                busyLabel = label
                busy = true
                notice = ""
                error = false
                try { block() } catch (e: Exception) {
                    error = true
                    notice = e.message ?: "Unexpected lab network error"
                    if (page == "Replay Lab") replayResult = notice
                    if (page == "Transfer") {
                        transferFeedback = notice
                        transferFeedbackIsError = true
                    }
                } finally { busy = false }
            }
        }
        fun signOut(returnToMode: Boolean = false) {
            // In-memory token is discarded immediately. Secure sessions are short-lived.
            api.logoutAsync(scope)
            username = ""
            password = ""
            page = if (returnToMode) "ModeSelect" else "Login"
            if (returnToMode) selectedMode = null
            sessionUser = null
            sessionMatches = emptyList()
            viewingTicket = null
            myAccount = null
            recentActivity = emptyList()
            selectedRecipient = null
            recipients = emptyList()
            notice = ""
        }
        Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (page != "ModeSelect" && page != "Login" && page != "Home" && page != "Transfer") {
                    Surface(color=Ink) {
                        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp, vertical=15.dp),
                            verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
                            Column {
                                Text("VulnBank  LAB", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Black, color=Accent)
                                Text(if(page=="Support") "Customer support" else if(page=="API Lab") "Account services" else if(page=="Replay Lab") "Transaction replay lab" else "More services",
                                    style=MaterialTheme.typography.labelSmall,color=Subdued)
                            }
                            TextButton(onClick={ signOut() }) {
                                Text("Sign out")
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (page != "Login" && page != "ModeSelect") {
                    NavigationBar(containerColor=Panel) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected=page == tab,
                                onClick={
                                    page=tab; viewingTicket=null; notice=""
                                    if (tab=="Transfer") {
                                        transferFeedback = ""
                                        run("Loading recipients") { recipients=api.recipients() }
                                    }
                                },
                                icon={ Icon(when(tab){
                                    "Home"->Icons.Rounded.Home
                                    "Transfer"->Icons.Rounded.SwapHoriz
                                    "Support"->Icons.Rounded.SupportAgent
                                    "API Lab"->Icons.Rounded.AccountBalanceWallet
                                    else->Icons.Rounded.MoreHoriz
                                }, contentDescription=tab) },
                                label={ Text(tab, maxLines=1) }
                            )
                        }
                    }
                }
            },
            containerColor=Ink
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (page != "ModeSelect" && page != "Login") {
                    val secure = selectedMode == LabMode.SECURE
                    Surface(color=if (secure) Color(0xFF123D39) else Color(0xFF513024)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal=15.dp, vertical=7.dp),
                            horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
                            Text(if (secure) "SECURE · HARDENED DEMO" else "VULNERABLE · TRAINING LAB",
                                color=if(secure) Color(0xFF60E5B3) else Color(0xFFFFAE88),
                                style=MaterialTheme.typography.labelSmall, fontWeight=FontWeight.Bold)
                            TextButton(onClick={signOut(returnToMode=true)}) { Text("Switch mode",color=Color.White) }
                        }
                    }
                }
                if (notice.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.padding(horizontal=16.dp)) { InfoCard(notice, if (error) Bad else Accent) }
                }
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(260)) +
                            slideInHorizontally(animationSpec = tween(310)) { it / 10 }) togetherWith
                        (fadeOut(animationSpec = tween(160)) +
                            slideOutHorizontally(animationSpec = tween(220)) { -it / 12 })
                    },
                    label = "VulnBank page navigation",
                    modifier = Modifier.weight(1f)
                ) { visiblePage ->
                when(visiblePage) {
                    "ModeSelect" -> ModeSelectionScreen(
                        busy=busy,
                        onChoose={ mode ->
                            val candidate = BankApi(mode)
                            run("Connecting to ${mode.title} lab") {
                                candidate.verifyMode()
                                selectedMode = mode
                                page = "Login"
                                notice = ""
                            }
                        }
                    )
                    "Login" -> PremiumLoginScreen(username, { username=it }, password, { password=it }, busy,
                        onLogin={ run("Signing you in") {
                            val response=api.login(username,password)
                            // Never persist a secure session token in Android preferences.
                            if (selectedMode == LabMode.VULNERABLE)
                                prefs.edit().putString("demo_session",response.token).apply()
                            api.setSession(response.token)
                            sessionUser=response.users.firstOrNull()
                            sessionMatches=response.users
                            bypass=response.bypassDetected
                            notice=""
                            page="Home"
                            sessionUser?.let { current ->
                                myAccount=api.accountDetails(current.id)
                                recentActivity=api.transactionDetails(current.id)
                            }
                        } })
                    "Home" -> PremiumHomeScreen(
                        user=sessionUser,account=myAccount,transactions=recentActivity,
                        bypass=bypass,matches=sessionMatches,customers=customers,
                        onRefresh={run("Refreshing your account") {
                            sessionUser?.let { myAccount=api.accountDetails(it.id); recentActivity=api.transactionDetails(it.id) }
                        }},
                        onCustomers={run("Loading your accounts") { customers=api.customers(); notice="Loaded ${customers.size} account profiles" }},
                        onTransfer={
                            page="Transfer"
                            transferFeedback=""
                            run("Loading recipients") { recipients=api.recipients() }
                        }, onSupport={page="Support"},
                        onServices={page="API Lab"}, onMore={page="More"},
                        onSignOut={signOut(); recipientSearch=""}
                    )
                    "Transfer" -> PremiumTransferScreen(
                        user=sessionUser, account=myAccount,
                        search=recipientSearch, onSearch={
                            recipientSearch=it
                            selectedRecipient=null
                            transferFeedback=""
                        },
                        recipients=recipients, selectedRecipient=selectedRecipient,
                        onSelectRecipient={ recipient ->
                            selectedRecipient=recipient
                            recipientSearch="${recipient.fullName} (@${recipient.username})"
                            transferFeedback=""
                        },
                        onReloadRecipients={
                            transferFeedback=""
                            run("Loading recipients") { recipients=api.recipients() }
                        },
                        amount=transferAmount,onAmount={transferAmount=it; transferFeedback=""},
                        note=transferNote,onNote={transferNote=it},
                        busy=busy,
                        feedback=transferFeedback, feedbackIsError=transferFeedbackIsError,
                        onBack={page="Home"},
                        onSubmit={
                            val recipient=selectedRecipient
                            val fromAccount=myAccount?.id
                            val amountValue=transferAmount.toDoubleOrNull()
                            val message = when {
                                fromAccount == null -> "Sign in and load your account before sending money."
                                recipient == null -> "Choose a recipient from the list above. You can search by name, @username, or account ID."
                                recipient.accountId == fromAccount -> "Choose an account different from your own."
                                amountValue == null || !amountValue.isFinite() || amountValue <= 0.0 -> "Enter a valid amount greater than zero."
                                amountValue > 2000.0 -> "Maximum simulated transfer is JOD 2,000."
                                else -> null
                            }
                            if (message != null) {
                                transferFeedback=message
                                transferFeedbackIsError=true
                            } else {
                                // Smart-cast values after the validation guards.
                                run("Processing your transfer") {
                                    val sourceId=requireNotNull(fromAccount)
                                    val target=requireNotNull(recipient)
                                    val amount=requireNotNull(amountValue)
                                    val result=api.transfer(
                                        sourceId, target.accountId, amount, transferNote,
                                        fromUsername=sessionUser?.username, toUsername=target.username
                                    )
                                    transferFeedback="Transfer #${result.transferId} sent to ${result.toFullName} (@${result.toUsername}, Account #${result.toAccountId})."
                                    transferFeedbackIsError=false
                                    notice=""
                                    transferNote=""
                                    myAccount=api.accountDetails(sourceId)
                                    recentActivity=api.transactionDetails(sourceId)
                                    // Retain recipient selection so user can see the confirmation.
                                }
                            }
                        }
                    )
                    "Support" -> {
                        Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
                        if (viewingTicket != null) {
                            val ticket=viewingTicket!!
                            Column(Modifier.fillMaxSize()) {
                                TextButton(onClick={ viewingTicket=null }) { Text("‹ Back to tickets") }
                                Text(ticket.subject,style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
                                Text("Support request details",color=Subdued,style=MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                                if (selectedMode == LabMode.SECURE) {
                                    PanelCard { Text(ticket.message, color=Color.White) }
                                } else {
                                    Box(Modifier.fillMaxSize()) { VulnerableTicketWebView(ticket) }
                                }
                            }
                        } else SupportScreen(
                            subject,{subject=it},ticketMessage,{ticketMessage=it},tickets,busy,
                            onSend={ run("Sending your support request") {
                                val id=api.submitTicket(sessionUser?.id,subject,ticketMessage)
                                ticketMessage=""
                                notice="✓ Support request #$id submitted successfully"
                                tickets=api.tickets()
                            } },
                            onLoad={ run("Refreshing support tickets") { tickets=api.tickets(); notice="Loaded ${tickets.size} demo support tickets" } },
                            onOpen={ viewingTicket=it }
                        )
                        }
                    }
                    "API Lab" -> Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) { ApiLabScreen(
                        apiOutput,accountId,{accountId=it},busy,
                        onHealth={run("Checking service status") { apiOutput=api.health() }},
                        onCustomers={run("Loading account profiles") {
                            customers=api.customers()
                            apiOutput=customers.joinToString(prefix="[\n",postfix="\n]",separator=",\n") { "  ${it.id}. ${it.fullName} <${it.email}>" }
                        }},
                        onAccount={run("Loading account details") { apiOutput=api.account(accountId.toLongOrNull() ?: 1L) }},
                        onTransactions={run("Refreshing transactions") { apiOutput=api.transactions(accountId.toLongOrNull() ?: 1L) }},
                        onReplayLab={
                            page="Replay Lab"
                            replayResult="Choose Run Replay Test to send the same transfer twice."
                            run("Loading demo recipients") { recipients=api.recipients() }
                        }
                    ) }
                    "Replay Lab" -> ReplayLabScreen(
                        mode=selectedMode ?: LabMode.VULNERABLE,
                        result=replayResult, busy=busy,
                        onBack={page="API Lab"},
                        onReplay={
                            run("Testing duplicate transfer processing") {
                                val account = myAccount ?: throw IllegalStateException("Sign in and load your account first")
                                val target = recipients.firstOrNull { it.accountId != account.id }
                                    ?: throw IllegalStateException("Recipient directory is empty")
                                val replayKey = UUID.randomUUID().toString()
                                val before = api.accountDetails(account.id).balance
                                val first = api.transfer(account.id,target.accountId,5.00,
                                    "Idempotency replay demonstration",
                                    sessionUser?.username,target.username,replayKey)
                                val second = api.transfer(account.id,target.accountId,5.00,
                                    "Idempotency replay demonstration",
                                    sessionUser?.username,target.username,replayKey)
                                val after = api.accountDetails(account.id).balance
                                myAccount=api.accountDetails(account.id)
                                recentActivity=api.transactionDetails(account.id)
                                replayResult="""First transfer ID: ${first.transferId}
                                    |Repeated transfer ID: ${second.transferId}
                                    |Same operation key: $replayKey
                                    |First balance: JOD ${"%.2f".format(java.util.Locale.US,before)}
                                    |Final balance: JOD ${"%.2f".format(java.util.Locale.US,after)}
                                    |Actual debit: JOD ${"%.2f".format(java.util.Locale.US,before-after)}
                                    |Duplicate recognized: ${second.replayed}
                                    |Expected: Vulnerable = 10.00 JOD, Secure = 5.00 JOD""".trimMargin()
                            }
                        }
                    )
                    "More" -> Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) { SettingsScreen(rootSimulated,{ checked ->
                        rootSimulated=checked
                        prefs.edit().putBoolean("simulate_root",checked).apply()
                    },rootResult,authResult,
                    onCheckRoot={rootResult=if (RootGate(context).isRooted()) "Device integrity check complete" else "Device integrity check complete"},
                    onCheckVip={authResult=if (AuthGate(context).isAuthenticated()) "Local access enabled" else "Local access restricted"},
                    onCallSecret={
                        if (selectedMode == LabMode.VULNERABLE) LabSecrets().getLabApiKey()
                        notice="Service configuration refreshed"
                    }, onGoLogin={ signOut() }) }
                }
                } // AnimatedContent
            }
        }
        AnimatedVisibility(
            visible = (busy || navigating) && !showSplash,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(230))
        ) {
            VulnBankLoadingOverlay(
                title = if (busy) busyLabel else when (page) {
                    "Home" -> "Opening your dashboard"
                    "Transfer" -> "Opening transfers"
                    "Support" -> "Opening support"
                    "API Lab" -> "Opening account services"
                    "More" -> "Opening settings"
                    else -> "Welcome back"
                },
                startup = false
            )
        }
        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(450))
        ) {
            VulnBankLoadingOverlay(title = "Welcome to VulnBank", startup = true)
        }
        } // Full-screen animation host
    }
}

@Composable private fun InfoCard(message: String, color: Color) {
    Surface(color=Panel,shape=MaterialTheme.shapes.medium,border=BorderStroke(1.dp,color.copy(alpha=.45f))) {
        Text(message, modifier=Modifier.fillMaxWidth().padding(12.dp),color=color,style=MaterialTheme.typography.bodyMedium)
    }
}
@Composable private fun Section(title: String, subtitle: String) {
    Spacer(Modifier.height(18.dp))
    Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
    Text(subtitle,color=Subdued,style=MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(14.dp))
}
@Composable private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Panel),border=BorderStroke(1.dp,Color(0xFF254B79))) {
        Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
@Composable private fun SupportScreen(
    subject:String,onSubject:(String)->Unit,message:String,onMessage:(String)->Unit,
    tickets:List<DemoTicket>,busy:Boolean,onSend:()->Unit,onLoad:()->Unit,onOpen:(DemoTicket)->Unit
) {
    LazyColumn(contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Section("Support center","We are here to help with your account") }
        item { PanelCard {
            OutlinedTextField(value=subject,onValueChange=onSubject,label={Text("Subject")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=message,onValueChange=onMessage,label={Text("Describe your issue")},modifier=Modifier.fillMaxWidth(),minLines=4,maxLines=7)
            Button(onClick=onSend,enabled=!busy && message.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("Send support request") }
        } }
        item { OutlinedButton(onClick=onLoad,enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("↻ Load support tickets") } }
        items(tickets,key={it.id}) { ticket ->
            Card(onClick={onOpen(ticket)},colors=CardDefaults.cardColors(containerColor=Panel),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp)) {
                    Text("#${ticket.id}   ${ticket.subject}",fontWeight=FontWeight.Bold)
                    Text(ticket.message,maxLines=2,overflow=TextOverflow.Ellipsis,color=Subdued)
                    Text("View conversation",color=Accent,style=MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
@Composable private fun ApiLabScreen(
    output:String,accountId:String,onAccountId:(String)->Unit,busy:Boolean,
    onHealth:()->Unit,onCustomers:()->Unit,onAccount:()->Unit,onTransactions:()->Unit,
    onReplayLab:()->Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Section("Account services","Account data and transaction requests")
        PanelCard {
            Text("Requests",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Button(onClick=onHealth,enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("GET /health") }
            OutlinedButton(onClick=onCustomers,enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("GET /api/v1/customers") }
            OutlinedTextField(value=accountId,onValueChange=onAccountId,modifier=Modifier.fillMaxWidth(),label={Text("Account ID to enumerate (1-12)")},singleLine=true)
            OutlinedButton(onClick=onAccount,enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("GET /api/v1/accounts/{id}") }
            OutlinedButton(onClick=onTransactions,enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("GET /api/v1/transactions") }
            Button(onClick=onReplayLab,enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Transaction Replay & Idempotency Lab →") }
        }
        Spacer(Modifier.height(12.dp))
        PanelCard {
            Text("Server response",fontWeight=FontWeight.Bold)
            Text(output,color=Subdued,style=MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(15.dp))
        Text("Account information is updated on request.",color=Subdued,style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun SettingsScreen(
    simulated:Boolean,onSimulated:(Boolean)->Unit,rootResult:String?,authResult:String?,
    onCheckRoot:()->Unit,onCheckVip:()->Unit,onCallSecret:()->Unit,onGoLogin:()->Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Section("Settings","App preferences and device information")
        PanelCard {
            Text("01  DEVICE INTEGRITY",fontWeight=FontWeight.Bold,color=Accent)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Device testing mode",color=Subdued)
                Switch(checked=simulated,onCheckedChange=onSimulated)
            }
            Button(onClick=onCheckRoot,modifier=Modifier.fillMaxWidth()) { Text("Run device check") }
            if (rootResult!=null) Text(rootResult,color=Subdued)
        }
        Spacer(Modifier.height(12.dp))
        PanelCard {
            Text("02  ACCOUNT ACCESS",fontWeight=FontWeight.Bold,color=Accent)
            Text("Review your app access status.",color=Subdued)
            Button(onClick=onCheckVip,modifier=Modifier.fillMaxWidth()) { Text("Review access") }
            if (authResult!=null) Text(authResult,color=Subdued)
        }
        Spacer(Modifier.height(12.dp))
        PanelCard {
            Text("03  APPLICATION SERVICES",fontWeight=FontWeight.Bold,color=Accent)
            Text("Refresh account service configuration.",color=Subdued)
            Button(onClick=onCallSecret,modifier=Modifier.fillMaxWidth()) { Text("Refresh services") }
        }
        Spacer(Modifier.height(12.dp))
        PanelCard {
            Text("04  NETWORK SETTINGS",fontWeight=FontWeight.Bold,color=Accent)
            Text("Network connectivity is managed by this app.",color=Subdued)
            OutlinedButton(onClick=onGoLogin,modifier=Modifier.fillMaxWidth()) { Text("Back to sign in") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** DELIBERATE VULNERABILITY: untrusted message interpolated unescaped into HTML with JavaScript enabled. */
@SuppressLint("SetJavaScriptEnabled")
@Composable private fun VulnerableTicketWebView(ticket: DemoTicket) {
    val context=LocalContext.current
    val html="""<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><style>
        body{font-family:sans-serif;background:#f8fafc;color:#17253a;padding:18px}
        .message{border:1px solid #d5dfe8;padding:16px;border-radius:12px;background:white}
        </style></head><body><h3>Support ticket #${ticket.id}</h3>
        <p>Message details:</p>
        <div class="message">${ticket.message}</div></body></html>"""
    AndroidView(modifier=Modifier.fillMaxSize(),factory={
        WebView(context).apply {
            settings.javaScriptEnabled=true
            settings.allowFileAccess=false
            settings.allowContentAccess=false
            settings.domStorageEnabled=false
            webChromeClient=object:WebChromeClient() {
                override fun onJsAlert(view:WebView?,url:String?,message:String?,result:JsResult?):Boolean {
                    AlertDialog.Builder(context).setTitle("VulnBank · Notice")
                        .setMessage(message ?: "XSS")
                        .setPositiveButton("OK") { _, _ -> result?.confirm() }
                        .setOnCancelListener { result?.cancel() }.show()
                    return true
                }
            }
            loadDataWithBaseURL("https://vulnbank.lab/",html,"text/html","UTF-8",null)
        }
    })
}

/** Two identical fictional transfers, one stable Idempotency-Key, two server modes. */
@Composable private fun ReplayLabScreen(
    mode: LabMode, result: String, busy: Boolean,
    onBack: () -> Unit, onReplay: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp)) {
        TextButton(onClick=onBack) { Text("‹ Back to API Lab") }
        Section("Transaction Replay", "Idempotency and duplicate payment processing")
        PanelCard {
            Text(if(mode==LabMode.SECURE) "SECURE BACKEND" else "VULNERABLE BACKEND",
                fontWeight=FontWeight.Bold,
                color=if(mode==LabMode.SECURE) Color(0xFF54E7AA) else Color(0xFFFFB38D))
            Text("This test sends the SAME synthetic JOD 5 transfer twice with the SAME Idempotency-Key.",
                color=Subdued)
            Text(if(mode==LabMode.SECURE)
                "Expected: one debit and the original transfer ID returned on retry."
                else "Expected: two debits because the vulnerable backend ignores the replay key.",color=Subdued)
            Button(onClick=onReplay,enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                Text("Run Replay Test")
            }
        }
        Spacer(Modifier.height(12.dp))
        PanelCard {
            Text("Test result",fontWeight=FontWeight.Bold)
            Text(result,color=Color.White,style=MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
    }
}
