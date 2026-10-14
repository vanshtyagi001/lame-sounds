package com.lamesounds.mobile.ui.components

import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lamesounds.mobile.models.ButtonShape
import com.lamesounds.mobile.models.SoundButtonConfig
import com.lamesounds.mobile.models.TextPosition
import com.lamesounds.mobile.ui.theme.*
import java.io.File

@Composable
fun SoundCard(
    config: SoundButtonConfig,
    modifier: Modifier = Modifier,
    isPressed: Boolean = false,
    isDragging: Boolean = false,
    isEditMode: Boolean = false,
    onDeleteClick: (() -> Unit)? = null,
    onEditClick: (() -> Unit)? = null,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    hapticEnabled: Boolean = true,
    isLandscape: Boolean = false
) {
    val view = LocalView.current

    // Fast, responsive spring depression animation for the inner button
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 800f),
        label = "ButtonScale"
    )

    // Outer Box: Rounded hardware slot socket
    val outerBoxShape = remember(isLandscape) { RoundedCornerShape(if (isLandscape) 14.dp else 16.dp) }

    // Inner Button Shape: Sits inside the outer rounded square box
    val innerButtonShape: Shape = remember(config.shape, config.cornerRadius) {
        when (config.shape) {
            ButtonShape.RECTANGLE -> RoundedCornerShape(8.dp)
            ButtonShape.ROUNDED_RECTANGLE -> RoundedCornerShape(config.cornerRadius.coerceAtLeast(10).dp)
            ButtonShape.CIRCLE -> CircleShape
            ButtonShape.HEXAGON -> HexagonShape()
        }
    }

    // Resolve Color / Gradient
    val primaryColor = remember(config.colorHex) { parseColor(config.colorHex, Color(0xFF1E1E1E)) }
    val gradientColor = remember(config.gradientHex, primaryColor) {
        config.gradientHex?.let { parseColor(it, primaryColor) } ?: primaryColor
    }
    val backgroundBrush = remember(primaryColor, gradientColor) {
        Brush.linearGradient(listOf(primaryColor, gradientColor))
    }
    val textColor = remember(config.textColorHex) { parseColor(config.textColorHex, Color.White) }

    // Resolve Image Artwork Bitmap if configured
    val imageBitmap = remember(config.imagePath) {
        if (!config.imagePath.isNullOrBlank()) {
            val file = File(config.imagePath)
            if (file.exists()) {
                try {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            } else null
        } else null
    }

    val verticalAlignment = remember(config.textPosition) {
        when (config.textPosition) {
            TextPosition.TOP -> Alignment.TopCenter
            TextPosition.CENTER -> Alignment.Center
            TextPosition.BOTTOM -> Alignment.BottomCenter
        }
    }

    // 1. Outer Box: Generous touch surface area
    Box(
        modifier = modifier
            .aspectRatio(if (isLandscape) 1.25f else 1.0f)
            .clip(outerBoxShape)
            .background(SurfaceDark)
            .border(
                1.dp,
                if (isDragging) StatusGreen else if (isEditMode) StatusGreen.copy(alpha = 0.6f) else HairlineBorder,
                outerBoxShape
            )
            .padding(if (isLandscape) 3.dp else 4.dp),
        contentAlignment = Alignment.Center
    ) {
        // 2. Inner Button: Sits comfortably inside the outer box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(innerButtonShape)
                .background(backgroundBrush)
                .border(
                    1.dp,
                    if (isDragging) StatusGreen.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.12f),
                    innerButtonShape
                )
        ) {
            // Artwork Image Layer
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = config.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Bottom Gradient Scrim for readable label text
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )
            }

            // Text Label Layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = verticalAlignment
            ) {
                Text(
                    text = config.label,
                    fontSize = (config.fontSizeSp + if (isLandscape) 1 else 0).coerceAtLeast(13).sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.02).em,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun parseColor(hex: String, fallback: Color): Color {
    return try {
        val clean = hex.removePrefix("#")
        val parsed = clean.toLong(16)
        if (clean.length <= 6) Color(parsed or 0xFF000000) else Color(parsed)
    } catch (e: Exception) {
        fallback
    }
}
