package com.vulnbank.lab.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vulnbank.lab.R

/**
 * Pure Compose visual animation. Does not touch network, login state, proxy or backend.
 * startup=true shows an opaque brand splash; false is a blocking, translucent loading overlay.
 */
@Composable
fun VulnBankLoadingOverlay(title: String, startup: Boolean) {
    val animation = rememberInfiniteTransition(label = "VulnBank brand motion")
    val angle by animation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1550, easing = androidx.compose.animation.core.LinearEasing)),
        label = "Spinner rotation"
    )
    val breathe by animation.animateFloat(
        initialValue = 0.975f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 950),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Logo breathing"
    )
    val backdrop = if (startup) {
        Brush.verticalGradient(
            listOf(Color(0xFF020F23), Color(0xFF072954), Color(0xFF03162E))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xF7021129), Color(0xF3072855), Color(0xF7021129))
        )
    }
    Box(
        modifier = Modifier.fillMaxSize().background(backdrop)
            .semantics { contentDescription = "$title. Please wait." },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.premium_shield),
                contentDescription = null,
                modifier = Modifier.size(if (startup) 184.dp else 136.dp)
                    .graphicsLayer(scaleX = breathe, scaleY = breathe),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(if (startup) 17.dp else 9.dp))
            Text(
                text = "VulnBank",
                color = Color.White,
                fontSize = if (startup) 30.sp else 23.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.3.sp
            )
            Text(
                text = "M O B I L E   B A N K I N G",
                color = Color(0xFF9DBCE2),
                fontSize = 9.sp,
                letterSpacing = 1.1.sp
            )
            Spacer(Modifier.height(30.dp))
            Box(
                modifier = Modifier.size(76.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(74.dp)) {
                    drawCircle(
                        color = Color(0xFF205A93).copy(alpha = .48f),
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
                Canvas(Modifier.size(74.dp).rotate(angle)) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color(0x0010DBFF),
                                Color(0xFF0CD5FF),
                                Color(0xFF137BFF),
                                Color(0x0010DBFF)
                            )
                        ),
                        startAngle = -90f,
                        sweepAngle = 278f,
                        useCenter = false,
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Canvas(Modifier.size(52.dp).rotate(-angle * 0.62f)) {
                    drawArc(
                        color = Color(0xFF78E8FF),
                        startAngle = -75f,
                        sweepAngle = 125f,
                        useCenter = false,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Box(Modifier.size(8.dp).background(Color(0xFF18DDFE), CircleShape))
            }
            Spacer(Modifier.height(25.dp))
            Text(
                text = title,
                color = Color(0xFFE9F7FF),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (startup) "Your banking experience starts here" else "Please wait a moment",
                color = Color(0xFF90B8DE),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
