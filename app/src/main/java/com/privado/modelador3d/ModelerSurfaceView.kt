package com.privado.modelador3d

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.sqrt
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ModelerSurfaceView(context: Context) : GLSurfaceView(context) {
    private val scene = SceneRenderer()
    private var previousX = 0f
    private var previousY = 0f
    private var previousDistance = 0f

    init {
        setEGLContextClientVersion(3)
        setRenderer(scene)
        renderMode = RENDERMODE_CONTINUOUSLY
        isFocusableInTouchMode = true
        requestFocus()
    }

    fun addCube() { scene.addCube(); requestRender() }
    fun duplicateSelected() { scene.duplicate(); requestRender() }
    fun deleteSelected() { scene.delete(); requestRender() }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
            deleteSelected(); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE) {
            if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
                scene.zoom(-event.getAxisValue(MotionEvent.AXIS_VSCROLL) * 0.3f); requestRender(); return true
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { previousX = event.x; previousY = event.y }
            MotionEvent.ACTION_POINTER_DOWN -> previousDistance = distance(event)
            MotionEvent.ACTION_MOVE -> if (event.pointerCount >= 2) {
                val d = distance(event)
                if (previousDistance > 0f) scene.zoom((previousDistance - d) * 0.012f)
                previousDistance = d
            } else {
                scene.orbit((event.x - previousX) * 0.5f, (event.y - previousY) * 0.5f)
                previousX = event.x; previousY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> previousDistance = 0f
        }
        requestRender(); return true
    }

    private fun distance(e: MotionEvent): Float {
        val dx = e.getX(0) - e.getX(1); val dy = e.getY(0) - e.getY(1)
        return sqrt(dx * dx + dy * dy)
    }
}

private class SceneRenderer : GLSurfaceView.Renderer {
    private var program = 0
    private var vertexBuffer = java.nio.ByteBuffer.allocateDirect(CUBE.size * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
    private var angleX = 25f
    private var angleY = -35f
    private var distance = 6f
    private var objects = 1
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)

    fun addCube() { objects++ }
    fun duplicate() { if (objects > 0) objects++ }
    fun delete() { if (objects > 0) objects-- }
    fun orbit(dx: Float, dy: Float) { angleY += dx; angleX = (angleX + dy).coerceIn(-89f, 89f) }
    fun zoom(amount: Float) { distance = (distance + amount).coerceIn(2f, 30f) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.07f, 0.08f, 0.1f, 1f)
        program = GLES30.glCreateProgram().also {
            GLES30.glAttachShader(it, shader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER))
            GLES30.glAttachShader(it, shader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER))
            GLES30.glLinkProgram(it)
        }
        vertexBuffer.put(CUBE).position(0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 45f, width.toFloat() / height, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        Matrix.setLookAtM(view, 0, 0f, 0f, distance, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, angleX, 1f, 0f, 0f); Matrix.rotateM(model, 0, angleY, 0f, 1f, 0f)
        Matrix.multiplyMM(mvp, 0, view, 0, model, 0); Matrix.multiplyMM(mvp, 0, projection, 0, mvp, 0)
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uMvp"), 1, false, mvp, 0)
        val pos = GLES30.glGetAttribLocation(program, "aPosition"); val color = GLES30.glGetAttribLocation(program, "aColor")
        vertexBuffer.position(0); GLES30.glVertexAttribPointer(pos, 3, GLES30.GL_FLOAT, false, 6 * 4, vertexBuffer); GLES30.glEnableVertexAttribArray(pos)
        vertexBuffer.position(3); GLES30.glVertexAttribPointer(color, 3, GLES30.GL_FLOAT, false, 6 * 4, vertexBuffer); GLES30.glEnableVertexAttribArray(color)
        repeat(objects) { GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 36) }
    }

    private fun shader(type: Int, source: String): Int = GLES30.glCreateShader(type).also { GLES30.glShaderSource(it, source); GLES30.glCompileShader(it) }
    companion object {
        private const val VERTEX_SHADER = "#version 300 es\nin vec3 aPosition; in vec3 aColor; out vec3 vColor; uniform mat4 uMvp; void main(){gl_Position=uMvp*vec4(aPosition,1.0);vColor=aColor;}"
        private const val FRAGMENT_SHADER = "#version 300 es\nprecision mediump float; in vec3 vColor; out vec4 outColor; void main(){outColor=vec4(vColor,1.0);}"
        private val CUBE = floatArrayOf(
            -1f,-1f,1f,1f,0.3f,0.3f, 1f,-1f,1f,1f,0.3f,0.3f, 1f,1f,1f,1f,0.3f,0.3f, -1f,-1f,1f,1f,0.3f,0.3f, 1f,1f,1f,1f,0.3f,0.3f, -1f,1f,1f,1f,0.3f,0.3f,
            -1f,-1f,-1f,0.3f,1f,0.4f, -1f,1f,-1f,0.3f,1f,0.4f, 1f,1f,-1f,0.3f,1f,0.4f, -1f,-1f,-1f,0.3f,1f,0.4f, 1f,1f,-1f,0.3f,1f,0.4f, 1f,-1f,-1f,0.3f,1f,0.4f,
            -1f,1f,-1f,0.3f,0.5f,1f, -1f,1f,1f,0.3f,0.5f,1f, 1f,1f,1f,0.3f,0.5f,1f, -1f,1f,-1f,0.3f,0.5f,1f, 1f,1f,1f,0.3f,0.5f,1f, 1f,1f,-1f,0.3f,0.5f,1f,
            -1f,-1f,-1f,1f,0.8f,0.2f, 1f,-1f,-1f,1f,0.8f,0.2f, 1f,-1f,1f,1f,0.8f,0.2f, -1f,-1f,-1f,1f,0.8f,0.2f, 1f,-1f,1f,1f,0.8f,0.2f, -1f,-1f,1f,1f,0.8f,0.2f,
            1f,-1f,-1f,0.8f,0.3f,1f, 1f,1f,-1f,0.8f,0.3f,1f, 1f,1f,1f,0.8f,0.3f,1f, 1f,-1f,-1f,0.8f,0.3f,1f, 1f,1f,1f,0.8f,0.3f,1f, 1f,-1f,1f,0.8f,0.3f,1f,
            -1f,-1f,-1f,0.2f,0.7f,1f, -1f,-1f,1f,0.2f,0.7f,1f, -1f,1f,1f,0.2f,0.7f,1f, -1f,-1f,-1f,0.2f,0.7f,1f, -1f,1f,1f,0.2f,0.7f,1f, -1f,1f,-1f,0.2f,0.7f,1f)
    }
}
