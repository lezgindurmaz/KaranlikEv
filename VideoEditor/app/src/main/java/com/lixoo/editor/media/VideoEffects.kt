package com.lixoo.editor.media

import androidx.media3.common.Effect
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.GrayscaleEffect

object VideoEffects {
    fun getGrayscale(): Effect = GrayscaleEffect()

    fun getBrightness(amount: Float): Effect = Brightness(amount)

    fun getContrast(amount: Float): Effect = Contrast(amount)
}
