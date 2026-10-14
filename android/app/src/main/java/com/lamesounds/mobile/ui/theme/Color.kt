package com.lamesounds.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Analogue Design System Palette (Editorial Monochrome & Precision Audio)
 * Canvas: Pure Black (#000000)
 * Chrome: Structural Greys & Pure White Ink
 * Content: Rich chromatic moments carried by sound cards
 */

// Core Canvas & Surfaces
val CanvasBlack = Color(0xFF000000)        // Pure black stage
val SurfaceDark = Color(0xFF101010)        // Nav bars, bottom dock, modal background
val SurfaceCard = Color(0xFF171717)        // Card surfaces & dialog containers
val SurfaceCardRaised = Color(0xFF222222)  // Interactive / hovered surfaces

// Hairlines & Borders
val HairlineBorder = Color(0xFF262626)     // Precision 1px dividers and borders
val HairlineBright = Color(0xFF404040)     // Active focus outlines

// Typography (Single-Family Monochrome)
val InkWhite = Color(0xFFFFFFFF)           // Primary text & High-contrast CTA fill
val InkMuted = Color(0xFFBFBFBF)           // Secondary text & metadata labels
val InkFaint = Color(0xFF737373)           // Tertiary captions & subtle hints

// Functional Audio Status Indicators (Hardware LED aesthetics)
val StatusGreen = Color(0xFF10B981)        // Connected / Sub-30ms low latency
val StatusAmber = Color(0xFFF59E0B)        // Medium latency / Auto-connecting
val StatusRed = Color(0xFFEF4444)          // Disconnected / Warning

// System-wide semantic bindings
val BgDarkest = CanvasBlack
val BgCard = SurfaceDark
val BgCardHover = SurfaceCardRaised
val BorderDark = HairlineBorder

val AccentPrimary = InkWhite               // Canonical high-contrast white pill CTA
val AccentSecondary = InkMuted
val SuccessGreen = StatusGreen
val WarningAmber = StatusAmber
val DangerRed = StatusRed

val TextPrimary = InkWhite
val TextSecondary = InkMuted
val TextMuted = InkFaint
