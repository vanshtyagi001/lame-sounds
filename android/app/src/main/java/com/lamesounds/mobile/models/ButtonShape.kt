package com.lamesounds.mobile.models

import kotlinx.serialization.Serializable

@Serializable
enum class ButtonShape {
    RECTANGLE,
    ROUNDED_RECTANGLE,
    CIRCLE,
    HEXAGON
}

@Serializable
enum class TextPosition {
    TOP,
    CENTER,
    BOTTOM
}
