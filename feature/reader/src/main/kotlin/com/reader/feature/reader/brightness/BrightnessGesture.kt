package com.reader.feature.reader.brightness

/** Brightness after a vertical drag. Up (negative delta) brightens; a full-height drag spans 0..1. */
fun nextBrightness(current: Float, dragDeltaPx: Float, heightPx: Float): Float {
    if (heightPx <= 0f) return current
    return (current - dragDeltaPx / heightPx).coerceIn(0f, 1f)
}
