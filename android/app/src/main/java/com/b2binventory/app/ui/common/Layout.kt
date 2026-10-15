package com.b2binventory.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Content never stretches wider than this on tablets; it is centred instead. */
val MaxContentWidth = 840.dp

enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

/** Window width class from the current configuration (phones are COMPACT, tablets MEDIUM/EXPANDED). */
@Composable
fun rememberWidthClass(): WidthClass {
    val width = LocalConfiguration.current.screenWidthDp
    return when {
        width < 600 -> WidthClass.COMPACT
        width < 840 -> WidthClass.MEDIUM
        else -> WidthClass.EXPANDED
    }
}

/** Centres [content] and limits its width on large screens. */
@Composable
fun ResponsiveContent(modifier: Modifier = Modifier, maxWidth: Dp = MaxContentWidth, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxWidth(), content = content)
    }
}
