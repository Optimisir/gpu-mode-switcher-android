package com.example.gpumode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Renderer(private val onStats: (String) -> Unit) : GLSurfaceView.Renderer {
    @Volatile var pending: Mode? = Modes.PERF
    private var cur = Modes.PERF

    private var prog = 0
    private var vbo = 0; private var ibo = 0; private var tex = 0
    private var indexCount = 0
    private var w = 1; private var h = 1
    private lateinit var base: Bitmap

    private var last = 0L; private var frames = 0; private var fpsT = 0L; private var fps = 0f
    private val proj = FloatArray(16); private val model = FloatArray(16); private val mvp = FloatArray(16)

    private val objs = arrayOf(
        floatArrayOf(0f, 0f, -6f, 1.4f, .4f), floatArrayOf(-2.6f, 1.2f, -8f, .6f, 1f),
        floatArrayOf(2.6f, -1f, -7f, .7f, -.8f), floatArrayOf(0f, -2.2f, -9f, .5f, 1.3f),
        floatArrayOf(-2.8f, -1.6f, -9f, .4f, .6f), floatArrayOf(2.4f, 2f, -10f, .5f, .9f)
    )

    private val VS = """#version 300 es
        in vec3 p; in vec2 t; uniform mat4 mvp, model; out vec3 vP, vN; out vec2 vT;
        void main(){ vec4 wp = model*vec4(p,1.); vP = wp.xyz; vN = mat3(model)*p; vT = t; gl_Position = mvp*vec4(p,1.); }"""
    private val FS = """#version 300 es
        precision highp float; in vec3 vP, vN; in vec2 vT; out vec4 o;
        uniform sampler2D tex; uniform bool flatS, post, spec;
        void main(){
          vec3 n = flatS ? normalize(cross(dFdx(vP), dFdy(vP))) : normalize(vN);
          vec3 L = normalize(vec3(.5,.7,.6)); vec3 V = normalize(vec3(0.,0.,6.)-vP);
          float d = max(dot(n,L),0.)*.85+.15;
          vec3 c = texture(tex,vT).rgb*d;
          if(spec){ c += pow(max(dot(reflect(-L,n),V),0.),48.)*.5; c += pow(1.-max(dot(n,V),0.),3.)*vec3(.2,.4,.7); }
          if(post) c = floor(c*4.+.5)/4.;
          o = vec4(c,1.); }"""

    private fun shader(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src); GLES30.glCompileShader(s)
        val ok = IntArray(1); GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException(GLES30.glGetShaderInfoLog(s))
        return s
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, shader(GLES30.GL_VERTEX_SHADER, VS))
        GLES30.glAttachShader(prog, shader(GLES30.GL_FRAGMENT_SHADER, FS))
        GLES30.glLinkProgram(prog); GLES30.glUseProgram(prog)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        base = makeBase()
        applyMode(cur)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        w = width; h = height
        GLES30.glViewport(0, 0, w, h)
        Matrix.perspectiveM(proj, 0, 52f, w.toFloat() / h, .1f, 50f)
    }

    private fun makeBase(): Bitmap {
        val b = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); val p = Paint()
        p.shader = LinearGradient(0f, 0f, 0f, 1024f,
            intArrayOf(0xFFDFEFFF.toInt(), 0xFF2E7D4F.toInt(), 0xFF2A6FB0.toInt(), 0xFF8A6A3A.toInt(), Color.WHITE),
            floatArrayOf(0f, .25f, .5f, .75f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 1024f, 1024f, p)
        p.shader = null
        val r = Random(7)
        repeat(600) {
            p.color = Color.HSVToColor(64, floatArrayOf(r.nextFloat() * 360, .5f, .6f))
            c.drawCircle(r.nextFloat() * 1024, r.nextFloat() * 1024, r.nextFloat() * 40 + 4, p)
        }
        return b
    }

    private fun applyMode(m: Mode) {
        cur = m
        val scaled = Bitmap.createScaledBitmap(base, m.tex, m.tex, !m.nearest)
        if (tex != 0) GLES30.glDeleteTextures(1, intArrayOf(tex), 0)
        val t = IntArray(1); GLES30.glGenTextures(1, t, 0); tex = t[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, scaled, 0)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        if (m.nearest) {
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        } else {
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            val ext = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
            if (m.aniso && ext.contains("GL_EXT_texture_filter_anisotropic")) {
                val mx = FloatArray(1); GLES30.glGetFloatv(0x84FF, mx, 0)
                GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, 0x84FE, mx[0])
            }
        }
        if (scaled !== base) scaled.recycle()
        buildSphere(m.seg)
    }

    private fun buildSphere(seg: Int) {
        val cols = seg * 2 + 1
        val v = ByteBuffer.allocateDirect((seg + 1) * cols * 5 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (i in 0..seg) { val th = i.toFloat() / seg * PI.toFloat()
            for (j in 0..seg * 2) { val u = j.toFloat() / (seg * 2); val ph = u * 2 * PI.toFloat()
                v.put(cos(ph) * sin(th)).put(cos(th)).put(sin(ph) * sin(th)).put(u).put(i.toFloat() / seg) } }
        v.position(0)
        val idx = IntBuffer.allocate(seg * seg * 2 * 6)
        for (i in 0 until seg) for (j in 0 until seg * 2) {
            val a = i * cols + j; val b = a + cols
            idx.put(a).put(b).put(a + 1).put(b).put(b + 1).put(a + 1) }
        idx.position(0); indexCount = idx.capacity()
        val ids = IntArray(2)
        if (vbo != 0) GLES30.glDeleteBuffers(2, intArrayOf(vbo, ibo), 0)
        GLES30.glGenBuffers(2, ids, 0); vbo = ids[0]; ibo = ids[1]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, v.capacity() * 4, v, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indexCount * 4, idx, GLES30.GL_STATIC_DRAW)
        val pl = GLES30.glGetAttribLocation(prog, "p"); val tl = GLES30.glGetAttribLocation(prog, "t")
        GLES30.glEnableVertexAttribArray(pl); GLES30.glVertexAttribPointer(pl, 3, GLES30.GL_FLOAT, false, 20, 0)
        GLES30.glEnableVertexAttribArray(tl); GLES30.glVertexAttribPointer(tl, 2, GLES30.GL_FLOAT, false, 20, 12)
    }

    override fun onDrawFrame(gl: GL10?) {
        pending?.let { pending = null; applyMode(it) }
        if (cur.fps > 0) {
            val wait = 1000L / cur.fps - (System.nanoTime() - last) / 1_000_000
            if (wait > 0) Thread.sleep(wait)
        }
        last = System.nanoTime()
        GLES30.glClearColor(.03f, .04f, .07f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        fun u(n: String) = GLES30.glGetUniformLocation(prog, n)
        GLES30.glUniform1i(u("flatS"), if (cur.flat) 1 else 0)
        GLES30.glUniform1i(u("post"), if (cur.post) 1 else 0)
        GLES30.glUniform1i(u("spec"), if (cur.spec) 1 else 0)
        val t = System.nanoTime() / 1e9f
        for (o in objs) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, o[0], o[1], o[2])
            Matrix.rotateM(model, 0, t * o[4] * 57.3f, 0f, 1f, 0f)
            Matrix.scaleM(model, 0, o[3], o[3], o[3])
            Matrix.multiplyMM(mvp, 0, proj, 0, model, 0)
            GLES30.glUniformMatrix4fv(u("model"), 1, false, model, 0)
            GLES30.glUniformMatrix4fv(u("mvp"), 1, false, mvp, 0)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)
        }
        frames++
        val now = System.nanoTime() / 1_000_000
        if (now - fpsT > 500) {
            fps = frames * 1000f / (now - fpsT); frames = 0; fpsT = now
            onStats("Mode:      ${cur.name}\nFPS:       ${fps.toInt()}${if (cur.fps > 0) " (cap ${cur.fps})" else " (uncapped)"}\n" +
                "Triangles: ${indexCount / 3 * objs.size}\nTexture:   ${cur.tex}x${cur.tex}\nRender:    ${w}x$h\n" +
                "Shading:   ${if (cur.flat) "flat" else "smooth"}${if (cur.spec) " + specular/rim" else ""}")
        }
    }
}
