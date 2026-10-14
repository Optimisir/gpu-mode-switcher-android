package com.example.gpumode

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var glView: GLSurfaceView
    private lateinit var stats: TextView
    private lateinit var renderer: Renderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 17, 21))
            setPadding(24, 48, 24, 24)
        }
        renderer = Renderer { text -> runOnUiThread { stats.text = text } }
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        root.addView(glView, LinearLayout.LayoutParams(-1, 0, 1f))

        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        fun btn(label: String, m: Mode) = Button(this).apply {
            text = label
            setOnClickListener { setMode(m) }
            row.addView(this, LinearLayout.LayoutParams(0, -2, 1f))
        }
        btn("🔋 Power", Modes.POWER)
        btn("🚀 Perf", Modes.PERF)
        btn("💎 Ultra", Modes.ULTRA)
        root.addView(row)

        stats = TextView(this).apply {
            setTextColor(Color.rgb(232, 234, 240))
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(0, 16, 0, 0)
        }
        root.addView(stats)
        setContentView(root)
    }

    private fun setMode(m: Mode) {
        renderer.pending = m
        val w = (glView.width * m.scale).toInt().coerceAtLeast(2)
        val h = (glView.height * m.scale).toInt().coerceAtLeast(2)
        glView.holder.setFixedSize(w, h)
    }

    override fun onPause() { super.onPause(); glView.onPause() }
    override fun onResume() { super.onResume(); glView.onResume() }
}
