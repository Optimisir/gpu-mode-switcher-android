package com.example.gpumode

data class Mode(
    val name: String,
    val seg: Int,
    val tex: Int,
    val nearest: Boolean,
    val flat: Boolean,
    val post: Boolean,
    val spec: Boolean,
    val aniso: Boolean,
    val scale: Float,
    val fps: Int
)

object Modes {
    val POWER = Mode("Power Saving (potato)", 7, 16, true, true, true, false, false, 0.4f, 30)
    val PERF = Mode("Performance", 32, 256, false, false, false, false, false, 1f, 60)
    val ULTRA = Mode("Ultra", 128, 1024, false, false, false, true, true, 1f, 0)
}
