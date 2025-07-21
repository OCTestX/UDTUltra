package io.github.octest.udtultra.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DelayShowAnimationFromTopLeft(
    delay: Long = 10,
    animationTime: Int = 500,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // 透明度动画
    val alpha = remember { Animatable(0.1f) }
    // 位移动画（左上角外偏移）
    val offset = remember { Animatable(-50f) }

    LaunchedEffect(Unit) {
        delay(delay)
        // 同时启动透明度与位移动画
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            offset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    Box(
        modifier = modifier
            .alpha(alpha.value)
            .offset(x = offset.value.dp, y = offset.value.dp)
    ) {
        content()
    }
}
