package com.c520.likexiang.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.c520.likexiang.MainActivity
import com.c520.likexiang.R
import com.c520.likexiang.data.MINUTE_MS
import com.c520.likexiang.data.Settings
import com.c520.likexiang.ui.AppIcon
import com.c520.likexiang.ui.Fmt
import com.c520.likexiang.ui.theme.Tok
import com.c520.likexiang.ui.theme.TypeTok
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import android.graphics.Color as AndroidColor

/** 響鈴畫面（照介面範例 C ④）：鎖定時也全螢幕顯示。鈴聲與震動由 RingService 負責，這裡只顯示內容與按鈕。 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 響鈴結束（按了通知上的關閉／稍後提醒、或響滿時間）就關掉；點到過期的通知、根本沒在響也直接關
        lifecycleScope.launch {
            val started = withTimeoutOrNull(3_000) { RingService.ringing.first { it.isNotEmpty() } }
            if (started != null) RingService.ringing.first { it.isEmpty() }
            if (!isFinishing) finish()
        }
        setContent {
            val items by RingService.ringing.collectAsStateWithLifecycle()
            // 一定要選關閉或稍後提醒：按返回鍵只關畫面不關鈴聲，會讓人以為關掉了
            BackHandler {}
            RingScreen(
                items = items,
                onSnooze = { minutes ->
                    RingService.snooze(this, minutes)
                    finish()
                },
                onDismiss = {
                    RingService.dismiss(this)
                    finish()
                },
                onOpen = ::openEvent,
            )
        }
    }

    /** 「打開行程」：先請使用者解鎖，解鎖成功才關鈴聲並開詳情；取消解鎖就繼續響。 */
    private fun openEvent(item: RingItem) {
        val open = {
            RingService.dismiss(this)
            startActivity(
                MainActivity.internalIntent(this, MainActivity.ACTION_OPEN_EVENT)
                    .putExtra(MainActivity.EXTRA_EVENT_ID, item.eventId)
                    .putExtra(MainActivity.EXTRA_BEGIN, item.begin)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            finish()
        }
        val km = getSystemService(KeyguardManager::class.java)
        if (km == null || !km.isKeyguardLocked) {
            open()
            return
        }
        km.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = open()
            },
        )
    }
}

@Composable
private fun RingScreen(
    items: List<RingItem>,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit,
    onOpen: (RingItem) -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(MINUTE_MS - now % MINUTE_MS)
        }
    }
    val single = items.singleOrNull()
    Box(Modifier.fillMaxSize().background(Tok.ringBg)) {
        DecoDots()
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Tok.sXl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Tok.sXl))
            Text("鬧鐘提醒", style = TypeTok.caption, color = Tok.ringInkMuted, modifier = Modifier.semantics { heading() })
            Text(Fmt.time(now), style = TypeTok.clock, color = Tok.ringInk)
            Text(Fmt.dateLong(LocalDate.now()), style = TypeTok.body, color = Tok.ringInkMuted)
            Spacer(Modifier.height(Tok.sLg))
            PulsingIcon()
            Spacer(Modifier.height(Tok.sLg))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Tok.sXs),
            ) {
                if (single != null) {
                    Text(single.title, style = TypeTok.ringTitle, color = Tok.ringInk, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(Notifications.describe(single), style = TypeTok.body, color = Tok.ringInkMuted, textAlign = TextAlign.Center)
                    startsIn(single, now)?.let { Pill(it) }
                } else {
                    items.forEach { item -> ItemCard(item, now, onOpen) }
                }
            }
            Spacer(Modifier.height(Tok.sMd))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tok.sXs)) {
                Settings.SNOOZE_OPTIONS.forEachIndexed { i, m ->
                    RingButton(
                        text = if (i == 0) "$m 分後再響" else "$m 分",
                        description = "$m 分鐘後再提醒",
                        background = Tok.ringSurface,
                        content = Tok.ringInk,
                        style = TypeTok.bodySm,
                        minHeight = Tok.ringSnoozeH,
                        modifier = Modifier.weight(1f),
                    ) { onSnooze(m) }
                }
            }
            Spacer(Modifier.height(Tok.sSm))
            RingButton("關閉", "關閉鬧鐘", Tok.primary, Tok.onPrimary, TypeTok.titleMd, Tok.ringDismissH, Modifier.fillMaxWidth(), onDismiss)
            if (single != null && !single.isTest) {
                Text(
                    "打開行程 ›",
                    style = TypeTok.bodySm,
                    color = Tok.ringInkMuted,
                    modifier = Modifier
                        .padding(top = Tok.sXs)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(Tok.rSm))
                        .clickable(role = Role.Button, onClickLabel = "關閉鬧鐘並打開這個行程") { onOpen(single) }
                        .padding(horizontal = Tok.sSm, vertical = Tok.sSm),
                )
            }
            Spacer(Modifier.height(Tok.sMd))
        }
    }
}

/** 「10 分鐘後開始」；已經開始或整天行程不顯示。 */
private fun startsIn(item: RingItem, now: Long): String? {
    if (item.allDay) return null
    val minutes = ((item.begin - now + MINUTE_MS - 1) / MINUTE_MS).toInt()
    return when {
        minutes <= 0 -> "已經開始"
        minutes < 60 -> "$minutes 分鐘後開始"
        minutes % 60 == 0 -> "${minutes / 60} 小時後開始"
        else -> "${minutes / 60} 小時 ${minutes % 60} 分後開始"
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        style = TypeTok.caption,
        color = Tok.ringInk,
        modifier = Modifier
            .padding(top = Tok.sXxs)
            .clip(RoundedCornerShape(Tok.rFull))
            .background(Tok.ringSurface)
            .padding(horizontal = Tok.sSm, vertical = Tok.sXxs),
    )
}

/** 同時有多個鬧鐘時，每個一張卡片，點了可以打開那個行程。 */
@Composable
private fun ItemCard(item: RingItem, now: Long, onOpen: (RingItem) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tok.rLg))
            .background(Tok.ringSurface)
            .then(if (item.isTest) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = "關閉鬧鐘並打開這個行程") { onOpen(item) })
            .padding(horizontal = Tok.sMd, vertical = Tok.sSm),
    ) {
        Text(item.title, style = TypeTok.titleMd, color = Tok.ringInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(Notifications.describe(item), startsIn(item, now)).joinToString("・"),
            style = TypeTok.bodySm,
            color = Tok.ringInkMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RingButton(
    text: String,
    description: String,
    background: Color,
    content: Color,
    style: TextStyle,
    minHeight: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(Tok.rMd))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = Tok.sXxs, vertical = Tok.sXs),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, color = content, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun PulsingIcon() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "scale",
    )
    val glow by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
        label = "alpha",
    )
    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = glow
                }
                .clip(CircleShape)
                .background(Tok.ringGlow),
        )
        Box(Modifier.size(72.dp).clip(CircleShape).background(Tok.ringSurface), contentAlignment = Alignment.Center) {
            AppIcon(R.drawable.ic_alarm, null, Tok.ringGlow, 36.dp)
        }
    }
}

/** 響鈴畫面四周的五色小圓點（DESIGN.md「響鈴畫面」）；避開畫面正上方的鏡頭位置。 */
@Composable
private fun DecoDots() {
    Canvas(Modifier.fillMaxSize()) {
        val spots = listOf(
            Triple(0.10f, 0.08f, 5f), Triple(0.86f, 0.12f, 7f), Triple(0.07f, 0.36f, 4f),
            Triple(0.91f, 0.41f, 5f), Triple(0.14f, 0.57f, 6f), Triple(0.82f, 0.60f, 4f),
        )
        spots.forEachIndexed { i, (x, y, r) ->
            drawCircle(Tok.deco[i % Tok.deco.size], radius = r.dp.toPx(), center = Offset(size.width * x, size.height * y))
        }
    }
}
