package com.vulnbank.lab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vulnbank.lab.network.LabMode

private val navy = Color(0xFF061A35)
private val green = Color(0xFF38DFA3)
private val coral = Color(0xFFFF9A7E)

@Composable
fun ModeSelectionScreen(onChoose: (LabMode) -> Unit, busy: Boolean) {
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF030E22),Color(0xFF092A52),Color(0xFF041329))))
            .verticalScroll(rememberScrollState())
            .padding(horizontal=22.dp, vertical=25.dp),
        horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Icon(Icons.Rounded.AccountBalance, contentDescription=null,
            tint=Color(0xFF51D4FF), modifier=Modifier.size(60.dp))
        Text("VulnBank LAB", color=Color.White, fontWeight=FontWeight.Black, fontSize=29.sp)
        Text("CHOOSE YOUR SECURITY ENVIRONMENT", color=Color(0xFFB7D4EA),
            fontWeight=FontWeight.Medium, fontSize=11.sp, letterSpacing=1.2.sp, textAlign=TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        ModeCard(
            name="VULNERABLE", subtitle="Offensive security testing",
            description="Deliberate SQL injection, BOLA / IDOR, Stored XSS and transaction replay. Intercept HTTP with Burp Suite.",
            accent=coral, mode=LabMode.VULNERABLE, busy=busy, onChoose=onChoose
        )
        ModeCard(
            name="SECURE", subtitle="Hardened comparison demo",
            description="Independent accounts, parameterized SQL, authenticated access, safe ticket rendering and transfer idempotency.",
            accent=green, mode=LabMode.SECURE, busy=busy, onChoose=onChoose
        )
        Text("Secure mode demonstrates specific server-side defenses. It is NOT a production-grade bank and still uses local HTTP.",
            color=Color(0xFFACCBE2), style=MaterialTheme.typography.bodySmall,
            textAlign=TextAlign.Center)
        Text("CREATED BY SHAMS SAMARA", color=Color(0xFF58CFFF),fontWeight=FontWeight.Bold,
            letterSpacing=1.sp, fontSize=11.sp)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ModeCard(
    name:String, subtitle:String, description:String,
    accent:Color, mode:LabMode, busy:Boolean, onChoose:(LabMode)->Unit
) {
    val shape=RoundedCornerShape(24.dp)
    Card(
        modifier=Modifier.fillMaxWidth().clickable(enabled=!busy) { onChoose(mode) },
        shape=shape,
        colors=CardDefaults.cardColors(containerColor=navy),
        border=BorderStroke(1.5.dp,accent.copy(alpha=.85f))
    ) {
        Column(Modifier.fillMaxWidth().padding(19.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(if(mode==LabMode.SECURE) Icons.Rounded.VerifiedUser else Icons.Rounded.BugReport,
                    contentDescription=null,tint=accent,modifier=Modifier.size(30.dp))
                Column {
                    Text(name,fontWeight=FontWeight.Black,fontSize=19.sp,color=accent,letterSpacing=.8.sp)
                    Text(subtitle,color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=13.sp)
                }
            }
            Text(description,color=Color(0xFFCADDED),style=MaterialTheme.typography.bodyMedium)
            HorizontalDivider(color=accent.copy(alpha=.22f))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                Text("ENTER ENVIRONMENT",fontWeight=FontWeight.Bold,color=accent,fontSize=12.sp)
                Spacer(Modifier.width(7.dp))
                Icon(Icons.Rounded.ArrowForward,contentDescription=null,tint=accent,modifier=Modifier.size(19.dp))
            }
        }
    }
}
