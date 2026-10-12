package com.c520.likexiang.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import com.c520.likexiang.R
import com.c520.likexiang.ui.theme.Tok

/** 1×1 語音新增小工具（WID-03）：只有一個麥克風，按了直接用語音新增行程。沒有資料，不用更新。 */
class VoiceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    provider = ImageProvider(R.drawable.ic_mic),
                    contentDescription = "用語音新增行程",
                    colorFilter = ColorFilter.tint(Widgets.color(Tok.onPrimary)),
                    modifier = GlanceModifier.size(48.dp).padding(12.dp).cornerRadius(Tok.rFull)
                        .background(Widgets.color(Tok.primary))
                        .clickable(actionStartActivity(Widgets.voice(LocalContext.current))),
                )
            }
        }
    }
}

class VoiceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VoiceWidget()
}
