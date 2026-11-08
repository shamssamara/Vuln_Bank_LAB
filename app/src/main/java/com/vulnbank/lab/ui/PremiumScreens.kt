package com.vulnbank.lab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import com.vulnbank.lab.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vulnbank.lab.network.DemoAccount
import com.vulnbank.lab.network.DemoCustomer
import com.vulnbank.lab.network.DemoRecipient
import com.vulnbank.lab.network.DemoTransaction
import java.util.Locale

// Fully native Jetpack Compose controls; this is NOT a static image or WebView.
private val Navy = Color(0xFF031229)
private val Navy2 = Color(0xFF061C3B)
private val DeepBlue = Color(0xFF07366F)
private val Cyan = Color(0xFF08D9F9)
private val Electric = Color(0xFF0976FF)
private val Royal = Color(0xFF0546E8)
private val Ice = Color(0xFFD7E8FF)
private val Muted = Color(0xFF94B4D7)
private val Glass = Color(0xFF0B264A)
private val Mint = Color(0xFF37E8BA)
private val ThinLine = Color(0xFF2871B5)
private val Cloud = Color.White

private fun money(value: Double): String = "JOD " + String.format(Locale.US, "%,.2f", value)

@Composable
private fun LuminousBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Navy,Navy2,Navy)))) {
        Canvas(Modifier.matchParentSize()) {
            val wave = Path().apply {
                moveTo(0f,size.height * 0.25f)
                cubicTo(size.width*.37f,size.height*.10f,size.width*.68f,size.height*.41f,size.width,size.height*.14f)
                lineTo(size.width,0f); lineTo(0f,0f); close()
            }
            drawPath(wave, brush=Brush.horizontalGradient(listOf(Color(0xFF03172E),Color(0xFF07469A),Color(0xFF0356D0))))
            val bottom=Path().apply {
                moveTo(0f,size.height*.93f)
                cubicTo(size.width*.30f,size.height*.89f,size.width*.60f,size.height*1.03f,size.width,size.height*.91f)
                lineTo(size.width,size.height);lineTo(0f,size.height);close()
            }
            drawPath(bottom,brush=Brush.horizontalGradient(listOf(Color(0xFF0658B5),Color(0xFF02367C),Color(0xFF074DD2))))
        }
        content()
    }
}

@Composable private fun GlassCard(
    modifier: Modifier=Modifier,
    content: @Composable ColumnScope.()->Unit
) {
    Column(
        modifier.clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xF00C2B54),Color(0xEF061B38),Color(0xF00A244C))))
            .border(BorderStroke(1.dp,Brush.linearGradient(listOf(Color(0xFF0EBBFF),Color(0xFF234F8E),Color(0xFF0B63E1)))),RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp),content=content
    )
}

@Composable private fun Brand(modifier:Modifier=Modifier, compact:Boolean=false) {
    Row(modifier, verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.AccountBalance,contentDescription=null,tint=Cyan,modifier=Modifier.size(if(compact)35.dp else 49.dp))
        Column(verticalArrangement=Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("VulnBank",fontSize=if(compact)24.sp else 32.sp,fontWeight=FontWeight.Black,color=Cloud)
                Spacer(Modifier.width(5.dp))
                Text("LAB",fontSize=if(compact)24.sp else 32.sp,fontWeight=FontWeight.Black,color=Electric)
            }
            Text("M O B I L E   B A N K I N G",fontSize=if(compact)9.sp else 10.sp,color=Ice)
        }
    }
}

@Composable private fun GlowAction(
    title:String,modifier:Modifier=Modifier,enabled:Boolean=true,onClick:()->Unit
) {
    val shape=RoundedCornerShape(19.dp)
    Box(modifier.height(56.dp).clip(shape)
        .background(Brush.horizontalGradient(listOf(Cyan,Electric,Royal)))
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick),contentAlignment=Alignment.Center) {
        Text(title,fontSize=18.sp,color=Color.White,fontWeight=FontWeight.Bold)
    }
}

@Composable private fun EmptyField(
    value:String,onChange:(String)->Unit,label:String,icon:ImageVector,
    modifier:Modifier=Modifier,secret:Boolean=false,keyboardType:KeyboardType=KeyboardType.Text
) {
    var showPassword by remember { mutableStateOf(false) }
    Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label,color=Ice,fontSize=12.sp,fontWeight=FontWeight.Medium)
        OutlinedTextField(
            value=value,onValueChange=onChange,singleLine=true,
            modifier=Modifier.fillMaxWidth().semantics { contentDescription=label },
            shape=RoundedCornerShape(18.dp),
            leadingIcon={Icon(icon,null,tint=Ice)},
            trailingIcon=if(secret) { {
                IconButton(onClick={showPassword=!showPassword}) {
                    Icon(if(showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription=if(showPassword)"Hide password" else "Show password",tint=Ice)
                }
            } } else null,
            visualTransformation=if(secret && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions=KeyboardOptions(keyboardType=if(secret)KeyboardType.Password else keyboardType,imeAction=ImeAction.Done),
            colors=OutlinedTextFieldDefaults.colors(
                focusedTextColor=Cloud,unfocusedTextColor=Cloud,
                focusedBorderColor=Cyan,unfocusedBorderColor=ThinLine,
                cursorColor=Cyan,focusedContainerColor=Glass,unfocusedContainerColor=Glass
            )
        )
    }
}

@Composable fun PremiumLoginScreen(
    username:String,onUsername:(String)->Unit,password:String,onPassword:(String)->Unit,
    busy:Boolean,onLogin:()->Unit
) {
    var forgotInfo by remember { mutableStateOf(false) }
    LuminousBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp),
            horizontalAlignment=Alignment.CenterHorizontally) {
            Spacer(Modifier.height(32.dp))
            Brand()
            Spacer(Modifier.height(25.dp))
            Image(
                painter=painterResource(R.drawable.premium_shield),
                contentDescription="Account sign-in",
                modifier=Modifier.width(244.dp).height(170.dp),
                contentScale=ContentScale.Fit
            )
            Spacer(Modifier.height(12.dp))
            Text("Welcome back",color=Cloud,fontWeight=FontWeight.Black,fontSize=33.sp)
            Spacer(Modifier.height(7.dp))
            Text("Sign in to your VulnBank account",color=Ice,fontSize=14.sp,textAlign=TextAlign.Center)
            Spacer(Modifier.height(26.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                EmptyField(username,onUsername,"Username",Icons.Rounded.Person,Modifier.fillMaxWidth())
                EmptyField(password,onPassword,"Password",Icons.Rounded.Lock,Modifier.fillMaxWidth(),secret=true)
                Spacer(Modifier.height(4.dp))
                GlowAction("Sign In",Modifier.fillMaxWidth(),enabled=!busy,onClick=onLogin)
                TextButton(onClick={forgotInfo=true},modifier=Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Forgot password?",color=Cyan)
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                MiniBrandFeature(Icons.Rounded.AccountCircle,"Accounts")
                MiniBrandFeature(Icons.Rounded.SwapHoriz,"Transfers")
                MiniBrandFeature(Icons.Rounded.SupportAgent,"Support")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (forgotInfo) AlertDialog(
        onDismissRequest={forgotInfo=false}, title={Text("Account assistance")},
        text={Text("Please contact the support team for account recovery.")},
        confirmButton={TextButton(onClick={forgotInfo=false}){Text("OK")}}
    )
}

@Composable private fun MiniBrandFeature(icon:ImageVector,label:String) {
    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Icon(icon,contentDescription=null,tint=Cyan,modifier=Modifier.size(25.dp))
        Text(label,fontSize=11.sp,color=Ice)
    }
}

@Composable private fun PremiumHeader(user:DemoCustomer?,onSignOut:()->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Brand(compact=true)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(38.dp).background(Color(0xFF113768),RoundedCornerShape(14.dp))
                .border(1.dp,ThinLine,RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center) {
                Icon(Icons.Rounded.Notifications,contentDescription="Notifications",tint=Ice,modifier=Modifier.size(22.dp))
            }
            Box(Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Electric,Cyan)))
                .clickable(role=Role.Button,onClick=onSignOut),contentAlignment=Alignment.Center) {
                Text(user?.fullName?.firstOrNull()?.uppercase()?:"U",color=Cloud,fontSize=20.sp,fontWeight=FontWeight.Black)
            }
        }
    }
}

@Composable private fun BalanceCard(account:DemoAccount?,onRefresh:()->Unit) {
    val round=RoundedCornerShape(25.dp)
    Column(Modifier.fillMaxWidth().shadow(8.dp,round).clip(round)
        .background(Brush.linearGradient(listOf(Color(0xFF0759B3),Color(0xFF073270),Color(0xFF062650))))
        .border(1.dp,Color(0xFF1EBCFF),round).padding(21.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.AccountBalanceWallet,null,tint=Cyan,modifier=Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(account?.accountType?.let { "$it Account" } ?: "Primary Account",fontWeight=FontWeight.Bold,color=Cloud,fontSize=17.sp)
                Text("•••• ${account?.id?.toString()?.padStart(4,'0')?:"----"}",color=Ice,fontSize=13.sp)
            }
            IconButton(onClick=onRefresh) { Icon(Icons.Rounded.Refresh,"Refresh balance",tint=Ice) }
        }
        Spacer(Modifier.height(5.dp))
        Text("Available Balance",fontSize=13.sp,color=Ice)
        Text(account?.balance?.let(::money)?:"—",fontSize=32.sp,fontWeight=FontWeight.Black,color=Cloud)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle,contentDescription=null,tint=Mint,modifier=Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Account overview",fontSize=11.sp,color=Ice)
        }
    }
}

@Composable private fun QuickAction(icon:ImageVector,title:String,onClick:()->Unit,modifier:Modifier=Modifier) {
    val shape=RoundedCornerShape(18.dp)
    Column(modifier.clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xFF0B4C91),Color(0xFF09214B))))
        .border(1.dp,ThinLine,shape).clickable(role=Role.Button,onClick=onClick)
        .padding(vertical=13.dp,horizontal=4.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Icon(icon,contentDescription=null,tint=Cyan,modifier=Modifier.size(26.dp))
        Text(title,color=Cloud,fontSize=11.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
    }
}

@Composable private fun HeadingRow(title:String,actionText:String="",onAction:(()->Unit)?=null) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Text(title,color=Cloud,fontSize=20.sp,fontWeight=FontWeight.Bold)
        if(onAction!=null) TextButton(onClick=onAction,contentPadding=PaddingValues(0.dp)) {
            Text(actionText,color=Cyan,fontSize=12.sp)
            Icon(Icons.Rounded.ArrowForward,contentDescription=null,tint=Cyan,modifier=Modifier.size(15.dp))
        }
    }
}

@Composable private fun ActivityRow(tx:DemoTransaction) {
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(43.dp).clip(CircleShape)
            .background(if(tx.amount>=0) Color(0xFF07594C) else Color(0xFF124788)),
            contentAlignment=Alignment.Center) {
            Icon(if(tx.amount>=0) Icons.Rounded.SouthWest else Icons.Rounded.NorthEast,
                contentDescription=null,tint=if(tx.amount>=0) Mint else Cyan,modifier=Modifier.size(21.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.description,color=Cloud,fontSize=13.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(tx.createdAt.take(16).replace('T',' '),color=Muted,fontSize=10.sp)
        }
        Text((if(tx.amount>=0) "+" else "") + money(tx.amount),
            color=if(tx.amount>=0) Mint else Cloud,fontWeight=FontWeight.Bold,fontSize=12.sp)
    }
}

/** Quiet, legible creator attribution shown once beside the signed-in greeting. */
@Composable
private fun CreatorSignatureByGreeting() {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(Color(0xFF0A294D))
            .border(1.dp, Color(0xFF2C5985), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(30.dp)
                .background(Cyan, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "CREATED BY",
                color = Cyan,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
                maxLines = 1
            )
            Text(
                text = "SHAMS SAMARA",
                color = Cloud,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 1
            )
        }
    }
}

@Composable fun PremiumHomeScreen(
    user:DemoCustomer?,account:DemoAccount?,transactions:List<DemoTransaction>,
    bypass:Boolean,matches:List<DemoCustomer>,customers:List<DemoCustomer>,
    onRefresh:()->Unit,onCustomers:()->Unit,onTransfer:()->Unit,onSupport:()->Unit,
    onServices:()->Unit,onMore:()->Unit,onSignOut:()->Unit
) {
    LuminousBackground {
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=22.dp,bottom=28.dp),
            verticalArrangement=Arrangement.spacedBy(17.dp)) {
            item { PremiumHeader(user,onSignOut) }
            item {
                Column {
                    Text("Welcome, ${user?.fullName?.substringBefore(' ')?:"customer"} 👋",color=Cloud,fontSize=23.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    CreatorSignatureByGreeting()
                    Spacer(Modifier.height(9.dp))
                    Text("Here's your account overview",color=Ice,fontSize=14.sp)
                }
            }
            item { BalanceCard(account,onRefresh) }
            item {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    QuickAction(Icons.Rounded.SwapHoriz,"Transfer",onTransfer,Modifier.weight(1f))
                    QuickAction(Icons.Rounded.CreditCard,"Accounts",onServices,Modifier.weight(1f))
                    QuickAction(Icons.Rounded.ReceiptLong,"Activity",onRefresh,Modifier.weight(1f))
                    QuickAction(Icons.Rounded.SupportAgent,"Support",onSupport,Modifier.weight(1f))
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    HeadingRow("Recent Transactions","Refresh",onRefresh)
                    if(transactions.isEmpty()) Text("No transactions yet",color=Muted)
                    transactions.take(5).forEach { tx ->
                        ActivityRow(tx)
                        if(tx != transactions.take(5).last()) HorizontalDivider(color=Color(0xFF234D74),thickness=0.5.dp)
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    HeadingRow("Your Accounts","Manage",onServices)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AccountBalanceWallet,null,tint=Cyan,modifier=Modifier.size(36.dp))
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(account?.accountType?.let { "$it Account" }?:"Primary Account",color=Cloud,fontWeight=FontWeight.SemiBold)
                            Text(account?.iban?:"Account information",color=Muted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        Text(account?.balance?.let(::money)?:"—",color=Cloud,fontSize=13.sp,fontWeight=FontWeight.Bold)
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    HeadingRow("Account Services","View all",onMore)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        ServiceChip(Icons.Rounded.Shield,"Security",onMore,Modifier.weight(1f))
                        ServiceChip(Icons.Rounded.AccountCircle,"Profiles",onCustomers,Modifier.weight(1f))
                        ServiceChip(Icons.Rounded.SupportAgent,"Help",onSupport,Modifier.weight(1f))
                    }
                    if(customers.isNotEmpty()) Text("${customers.size} customer profiles loaded",color=Ice,fontSize=12.sp)
                }
            }
            if(bypass) item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Multiple customer profiles (${matches.size})",fontWeight=FontWeight.Bold,color=Mint)
                    matches.forEach { Text("${it.fullName} · ${it.email}",fontSize=11.sp,color=Ice) }
                }
            }

        }
    }
}

@Composable private fun ServiceChip(icon:ImageVector,label:String,onClick:()->Unit,modifier:Modifier=Modifier) {
    Column(modifier.clip(RoundedCornerShape(13.dp)).background(Color(0xFF103763))
        .clickable(role=Role.Button,onClick=onClick).padding(10.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Icon(icon,contentDescription=null,tint=Cyan,modifier=Modifier.size(23.dp))
        Text(label,fontSize=11.sp,color=Cloud)
    }
}

@Composable fun PremiumTransferScreen(
    user:DemoCustomer?, account:DemoAccount?,
    search:String, onSearch:(String)->Unit,
    recipients:List<DemoRecipient>, selectedRecipient:DemoRecipient?,
    onSelectRecipient:(DemoRecipient)->Unit, onReloadRecipients:()->Unit,
    amount:String, onAmount:(String)->Unit, note:String, onNote:(String)->Unit,
    busy:Boolean, feedback:String, feedbackIsError:Boolean,
    onBack:()->Unit, onSubmit:()->Unit
) {
    val query = search.trim().removePrefix("@").lowercase(Locale.ROOT)
    val matches = remember(recipients,query) {
        if (query.isBlank()) recipients
        else recipients.filter { item ->
            item.username.lowercase(Locale.ROOT).contains(query) ||
                item.fullName.lowercase(Locale.ROOT).contains(query) ||
                item.accountId.toString().contains(query)
        }
    }
    LuminousBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp),
            verticalArrangement=Arrangement.spacedBy(18.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onBack) { Icon(Icons.Rounded.ArrowBack,"Back",tint=Cloud) }
                Spacer(Modifier.width(8.dp))
                Text("Transfer Money",color=Cloud,fontSize=24.sp,fontWeight=FontWeight.Black)
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccountBalanceWallet,null,tint=Cyan,modifier=Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("From account",color=Muted,fontSize=12.sp)
                        Text(user?.fullName ?: "Your account",color=Cloud,fontWeight=FontWeight.SemiBold)
                        Text("@${user?.username ?: "user"}  ·  Account #${account?.id ?: "—"}",color=Ice,fontSize=12.sp)
                    }
                }
                HorizontalDivider(color=ThinLine)
                Text("Available: ${account?.balance?.let(::money)?:"—"}",color=Ice)
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text("Recipient",color=Cloud,fontSize=19.sp,fontWeight=FontWeight.Bold)
                    TextButton(onClick=onReloadRecipients,enabled=!busy) {
                        Icon(Icons.Rounded.Refresh,null,tint=Cyan,modifier=Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh",color=Cyan,fontSize=12.sp)
                    }
                }
                EmptyField(search,onSearch,"Name, @username, or account ID",Icons.Rounded.Search)
                if (selectedRecipient != null) {
                    val recipient=selectedRecipient
                    Surface(
                        color=Color(0xFF0B4962), shape=RoundedCornerShape(16.dp),
                        border=BorderStroke(1.dp,Mint),modifier=Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle,null,tint=Mint,modifier=Modifier.size(23.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(recipient.fullName,color=Cloud,fontWeight=FontWeight.Bold)
                                Text("@${recipient.username}  ·  Account #${recipient.accountId}",color=Ice,fontSize=12.sp)
                            }
                        }
                    }
                    Text("To change recipient, edit the search field.",color=Muted,fontSize=11.sp)
                } else {
                    Text("Choose a recipient below. Each result shows the username and account ID.",
                        color=Muted,fontSize=12.sp)
                    if (recipients.isEmpty()) {
                        Text(
                            if (busy) "Loading recipients..." else "The recipient directory is empty. Tap Refresh. If an HTTP 404 appears, update the Kotlin server and retry.",
                            color=Ice, fontSize=12.sp
                        )
                    } else if (matches.isEmpty()) {
                        Text("No matching recipient. Try their name, @username or account number.",color=Ice,fontSize=12.sp)
                    } else {
                        matches.filter { it.accountId != account?.id }.take(6).forEach { recipient ->
                            val shape = RoundedCornerShape(14.dp)
                            Row(Modifier.fillMaxWidth().clip(shape)
                                .background(Color(0xFF103B70))
                                .clickable(role=Role.Button,onClick={onSelectRecipient(recipient)})
                                .padding(horizontal=12.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Person,null,tint=Cyan,modifier=Modifier.size(24.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(recipient.fullName,color=Cloud,fontWeight=FontWeight.Bold,fontSize=13.sp)
                                    Text("@${recipient.username}  ·  Account #${recipient.accountId}",color=Ice,fontSize=12.sp)
                                }
                                Icon(Icons.Rounded.ChevronRight,null,tint=Cyan,modifier=Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Payment details",color=Cloud,fontSize=19.sp,fontWeight=FontWeight.Bold)
                EmptyField(amount,onAmount,"Amount (JOD)",Icons.Rounded.Payments,keyboardType=KeyboardType.Decimal)
                EmptyField(note,onNote,"Reference (optional)",Icons.Rounded.ReceiptLong)
                if (feedback.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (feedbackIsError) Color(0xFF3A2239) else Color(0xFF073E48),
                        border = BorderStroke(1.dp, if (feedbackIsError) Color(0xFFEF7995) else Mint),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (feedbackIsError) Icons.Rounded.Info else Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = if (feedbackIsError) Color(0xFFFFAAB7) else Mint,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(feedback, fontSize=12.sp, color=Cloud, lineHeight=18.sp)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                // Keep the button active: invalid forms display an explanation instead
                // of seeming to ignore taps (e.g. when the directory request fails).
                GlowAction(
                    if (busy) "Processing..." else "Send Money",
                    Modifier.fillMaxWidth(), enabled=!busy, onClick=onSubmit
                )
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info,null,tint=Ice,modifier=Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Transfers are processed instantly.",color=Ice,fontSize=12.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
