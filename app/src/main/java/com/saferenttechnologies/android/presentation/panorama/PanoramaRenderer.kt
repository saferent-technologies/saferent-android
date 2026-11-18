package com.saferenttechnologies.android.presentation.panorama

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.saferenttechnologies.android.domain.model.PanoramaProjection
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * OpenGL ES 2.0 panorama: the photo is drawn on the inside of a sphere (2:1 photo sphere) or a
 * cylinder (phone panorama), camera in the middle. [yaw] / [pitch] / [fov] are set from touch or
 * the phone's rotation sensor (any thread → @Volatile).
 */
class PanoramaRenderer : GLSurfaceView.Renderer {
    @Volatile var yaw = 0f
    @Volatile var pitch = 0f
    @Volatile var fov = 75f

    @Volatile private var pending: Pair<Bitmap, PanoramaProjection>? = null
    private var program = 0
    private var texture = 0
    private var vertices: FloatBuffer? = null
    private var indices: ShortBuffer? = null
    private var indexCount = 0
    private var aspect = 1f
    private var maxTexture = 2048
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val mvp = FloatArray(16)

    /** Called from any thread; uploaded on the GL thread at the next frame. */
    fun setImage(bitmap: Bitmap, projection: PanoramaProjection) {
        pending = bitmap to projection
        if (!projection.sphere) pitch = 0f
    }

    /** Cylinder panoramas can't be looked at far up / down. */
    @Volatile var maxPitch = 85f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        program = link(VERTEX, FRAGMENT)
        val sizes = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, sizes, 0)
        maxTexture = sizes[0].coerceAtLeast(2048)
        texture = 0
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = width.toFloat() / height.coerceAtLeast(1)
    }

    override fun onDrawFrame(gl: GL10?) {
        pending?.let { (bmp, p) ->
            pending = null
            upload(bmp)
            buildMesh(p)
            maxPitch = if (p.sphere) 85f else ((p.verticalDeg - fov) / 2f).coerceAtLeast(0f)
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val v = vertices ?: return
        val i = indices ?: return
        if (texture == 0) return

        Matrix.perspectiveM(proj, 0, fov, aspect, 0.05f, 10f)
        Matrix.setIdentityM(view, 0)
        Matrix.rotateM(view, 0, pitch.coerceIn(-maxPitch, maxPitch), 1f, 0f, 0f)
        Matrix.rotateM(view, 0, yaw, 0f, 1f, 0f)
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0)

        GLES20.glUseProgram(program)
        val aPos = GLES20.glGetAttribLocation(program, "aPos")
        val aUv = GLES20.glGetAttribLocation(program, "aUv")
        val uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        val uTex = GLES20.glGetUniformLocation(program, "uTex")
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1i(uTex, 0)
        v.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, STRIDE, v)
        GLES20.glEnableVertexAttribArray(aPos)
        v.position(3)
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, STRIDE, v)
        GLES20.glEnableVertexAttribArray(aUv)
        i.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, i)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aUv)
    }

    private fun upload(source: Bitmap) {
        if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
        val bmp = if (source.width > maxTexture || source.height > maxTexture) {
            val s = minOf(maxTexture.toFloat() / source.width, maxTexture.toFloat() / source.height)
            Bitmap.createScaledBitmap(source, (source.width * s).toInt(), (source.height * s).toInt(), true)
        } else {
            source
        }
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texture = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        // Non-power-of-two textures need CLAMP on ES 2.0.
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        if (bmp !== source) bmp.recycle()
    }

    /** Inside of a sphere (photo sphere) or a cylinder strip (phone panorama), centred in front. */
    private fun buildMesh(p: PanoramaProjection) {
        val cols = 96
        val rows = if (p.sphere) 48 else 1
        val data = FloatArray((rows + 1) * (cols + 1) * 5)
        var k = 0
        val hRad = Math.toRadians(p.horizontalDeg.toDouble())
        val half = tan(Math.toRadians(p.verticalDeg / 2.0)).toFloat()
        for (r in 0..rows) {
            val v = r.toFloat() / rows
            for (c in 0..cols) {
                val u = c.toFloat() / cols
                // Left edge of the photo on the left, centred straight ahead (−z).
                val theta = -hRad / 2 + u * hRad
                val x: Float
                val y: Float
                val z: Float
                if (p.sphere) {
                    val phi = Math.PI / 2 - v * Math.PI // +90° (top) → −90° (bottom)
                    x = (cos(phi) * sin(theta)).toFloat()
                    y = sin(phi).toFloat()
                    z = (-cos(phi) * cos(theta)).toFloat()
                } else {
                    x = sin(theta).toFloat()
                    y = half - v * 2 * half
                    z = -cos(theta).toFloat()
                }
                data[k++] = x; data[k++] = y; data[k++] = z; data[k++] = u; data[k++] = v
            }
        }
        val idx = ShortArray(rows * cols * 6)
        var n = 0
        for (r in 0 until rows) for (c in 0 until cols) {
            val a = (r * (cols + 1) + c).toShort()
            val b = (a + cols + 1).toShort()
            idx[n++] = a; idx[n++] = b; idx[n++] = (a + 1).toShort()
            idx[n++] = (a + 1).toShort(); idx[n++] = b; idx[n++] = (b + 1).toShort()
        }
        vertices = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(data).also { it.position(0) }
        indices = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().put(idx).also { it.position(0) }
        indexCount = idx.size
    }

    private fun link(vs: String, fs: String): Int {
        fun compile(type: Int, src: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, compile(GLES20.GL_VERTEX_SHADER, vs))
            GLES20.glAttachShader(it, compile(GLES20.GL_FRAGMENT_SHADER, fs))
            GLES20.glLinkProgram(it)
        }
    }

    private companion object {
        const val STRIDE = 5 * 4
        const val VERTEX = """
            uniform mat4 uMvp;
            attribute vec3 aPos;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main() { vUv = aUv; gl_Position = uMvp * vec4(aPos, 1.0); }
        """
        const val FRAGMENT = """
            precision mediump float;
            uniform sampler2D uTex;
            varying vec2 vUv;
            void main() { gl_FragColor = texture2D(uTex, vUv); }
        """
    }
}
