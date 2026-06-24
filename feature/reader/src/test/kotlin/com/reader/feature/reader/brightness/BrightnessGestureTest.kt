package com.reader.feature.reader.brightness

import org.junit.Assert.assertEquals
import org.junit.Test

class BrightnessGestureTest {
    @Test fun swipe_up_increases() {
        assertEquals(0.6f, nextBrightness(0.5f, -100f, 1000f), 0.0001f)
    }

    @Test fun swipe_down_decreases() {
        assertEquals(0.4f, nextBrightness(0.5f, 100f, 1000f), 0.0001f)
    }

    @Test fun clamps_to_unit_range() {
        assertEquals(1f, nextBrightness(0.95f, -200f, 1000f), 0.0001f)
        assertEquals(0f, nextBrightness(0.05f, 200f, 1000f), 0.0001f)
    }

    @Test fun zero_height_is_safe() {
        assertEquals(0.5f, nextBrightness(0.5f, 100f, 0f), 0.0001f)
    }
}
