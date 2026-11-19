package com.privado.modelador3d

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ModelerSurfaceView(context: Context) : GLSurfaceView(context) {
    private val scene = SceneRenderer()
    var onSceneChanged: (() -> Unit)? = null
    private var previousX = 0f
    private var previousY = 0f
    private var previousDistance = 0f
    private var dragDistance = 0f

    init {
        setEGLContextClientVersion(3)
        setRenderer(scene)
        renderMode = RENDERMODE_CONTINUOUSLY
        isFocusableInTouchMode = true
        requestFocus()
    }

    fun addCube() { scene.addPrimitive("Cubo"); changed() }
    fun addPrimitive(kind: String) { scene.addPrimitive(kind); changed() }
    fun duplicateSelected() { scene.duplicate(); changed() }
    fun deleteSelected() { scene.delete(); changed() }
    fun clearScene() { scene.clear(); changed() }
    fun setTool(tool: String) { scene.setTool(tool); requestRender() }
    fun undo() { scene.undo(); changed() }
    fun redo() { scene.redo(); changed() }
    fun objectCount() = scene.objectCount()
    fun selectedName() = scene.selectedName()
    fun selectedTransform() = scene.selectedTransform()
    fun setTransform(component: Int, axis: Int, value: Float) { scene.setTransform(component, axis, value); changed() }
    fun addImportedAsset(name: String, path: String? = null) { scene.addImportedAsset(name, path); changed() }
    fun assignTexture(path: String) { scene.assignTexture(path); changed() }
    fun exportSceneJson() = scene.exportSceneJson()
    fun importSceneJson(json: String) { scene.importSceneJson(json); changed() }

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
            MotionEvent.ACTION_DOWN -> { previousX = event.x; previousY = event.y; dragDistance = 0f }
            MotionEvent.ACTION_POINTER_DOWN -> previousDistance = distance(event)
            MotionEvent.ACTION_MOVE -> if (event.pointerCount >= 2) {
                val d = distance(event)
                if (previousDistance > 0f) scene.zoom((previousDistance - d) * 0.012f)
                previousDistance = d
            } else {
                val dx = event.x - previousX; val dy = event.y - previousY
                dragDistance += kotlin.math.abs(dx) + kotlin.math.abs(dy)
                if (scene.isTransformTool()) scene.applyDrag(dx, dy) else scene.orbit(dx * 0.5f, dy * 0.5f)
                previousX = event.x; previousY = event.y
            }
            MotionEvent.ACTION_UP -> { if (dragDistance < 12f) scene.selectNext() else scene.finishTransform(); previousDistance = 0f; changed() }
            MotionEvent.ACTION_CANCEL -> previousDistance = 0f
        }
        requestRender(); return true
    }

    private fun changed() { requestRender(); post { onSceneChanged?.invoke() } }

    private fun distance(e: MotionEvent): Float {
        val dx = e.getX(0) - e.getX(1); val dy = e.getY(0) - e.getY(1)
        return sqrt(dx * dx + dy * dy)
    }
}

private data class SceneObject(
    var name: String,
    var kind: String,
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var rx: Float = 0f,
    var ry: Float = 0f,
    var rz: Float = 0f,
    var sx: Float = 1f,
    var sy: Float = 1f,
    var sz: Float = 1f,
    var texturePath: String? = null,
    var sourceAsset: String? = null
)

private class SceneRenderer : GLSurfaceView.Renderer {
    private var program = 0
    private val meshBuffers = mutableMapOf<String, java.nio.FloatBuffer>()
    private var angleX = 25f
    private var angleY = -35f
    private var distance = 6f
    private val objects = mutableListOf(SceneObject("Cubo", "Cubo"))
    private val externalMeshes = mutableMapOf<String, FloatArray>()
    private val undoStack = ArrayDeque<List<SceneObject>>()
    private val redoStack = ArrayDeque<List<SceneObject>>()
    private var selectedIndex = 0
    private var tool = "select"
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)

    @Synchronized fun addPrimitive(kind: String) {
        remember()
        val index = objects.size
        objects.add(SceneObject(kind + "_" + (index + 1), kind, x = (index % 4 - 1.5f) * 2.4f, z = (index / 4) * -2.4f))
        selectedIndex = objects.lastIndex
    }
    @Synchronized fun addImportedAsset(name: String, path: String? = null) {
        remember()
        val index = objects.size
        val extension = name.substringAfterLast('.', "").lowercase()
        val displayName = name.substringBeforeLast('.', name).ifBlank { "Modelo importado" }
        val importedPath = path
        if (importedPath != null) MeshImporter.read(File(importedPath))?.let { externalMeshes[importedPath] = it }
        objects.add(
            SceneObject(
                name = displayName + "_" + (index + 1),
                kind = "Cubo",
                x = (index % 4 - 1.5f) * 2.4f,
                z = (index / 4) * -2.4f,
                sourceAsset = path ?: "$name [$extension]"
            )
        )
        selectedIndex = objects.lastIndex
    }
    @Synchronized fun assignTexture(path: String) {
        val selected = objects.getOrNull(selectedIndex) ?: return
        remember()
        selected.texturePath = path
    }
    @Synchronized fun duplicate() {
        if (objects.isEmpty()) return
        remember()
        val source = objects[selectedIndex].copy(name = objects[selectedIndex].name + "_copia", x = objects[selectedIndex].x + 1f, z = objects[selectedIndex].z + 1f)
        objects.add(source); selectedIndex = objects.lastIndex
    }
    @Synchronized fun delete() {
        if (objects.isEmpty()) return
        remember()
        objects.removeAt(selectedIndex); selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
    }
    @Synchronized fun clear() { if (objects.isEmpty()) return; remember(); objects.clear(); selectedIndex = -1 }
    @Synchronized fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(objects.map { it.copy() }); objects.clear(); objects.addAll(undoStack.removeLast().map { it.copy() }); selectedIndex = if (objects.isEmpty()) -1 else selectedIndex.coerceIn(0, objects.lastIndex)
    }
    @Synchronized fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(objects.map { it.copy() }); objects.clear(); objects.addAll(redoStack.removeLast().map { it.copy() }); selectedIndex = if (objects.isEmpty()) -1 else selectedIndex.coerceIn(0, objects.lastIndex)
    }
    @Synchronized fun setTool(next: String) { tool = next }
    @Synchronized fun objectCount() = objects.size
    @Synchronized fun selectedName() = objects.getOrNull(selectedIndex)?.name
    @Synchronized fun selectedTransform(): FloatArray {
        val o = objects.getOrNull(selectedIndex) ?: return FloatArray(9) { if (it % 4 == 0) 1f else 0f }
        return floatArrayOf(o.x,o.y,o.z,o.rx,o.ry,o.rz,o.sx,o.sy,o.sz)
    }
    @Synchronized fun setTransform(component: Int, axis: Int, value: Float) {
        val o = objects.getOrNull(selectedIndex) ?: return
        when (component) { 0 -> when(axis) { 0 -> o.x=value; 1 -> o.y=value; else -> o.z=value }; 1 -> when(axis) { 0 -> o.rx=value; 1 -> o.ry=value; else -> o.rz=value }; else -> when(axis) { 0 -> o.sx=value; 1 -> o.sy=value; else -> o.sz=value } }
    }
    @Synchronized fun isTransformTool() = tool == "move" || tool == "rotate" || tool == "scale"
    @Synchronized fun selectNext() { if (objects.isNotEmpty()) selectedIndex = (selectedIndex + 1) % objects.size }
    @Synchronized fun applyDrag(dx: Float, dy: Float) {
        if (selectedIndex !in objects.indices) return
        val selected = objects[selectedIndex]
        when (tool) {
            "move" -> { selected.x += dx * 0.012f; selected.y -= dy * 0.012f }
            "rotate" -> { selected.ry += dx * 0.7f; selected.rx += dy * 0.7f }
            "scale" -> { val factor = (1f - dy * 0.008f).coerceIn(0.1f, 3f); selected.sx = (selected.sx * factor).coerceIn(0.1f, 10f); selected.sy = (selected.sy * factor).coerceIn(0.1f, 10f); selected.sz = (selected.sz * factor).coerceIn(0.1f, 10f) }
        }
    }
    @Synchronized fun finishTransform() {
        if (tool != "move" || selectedIndex !in objects.indices) return
        val o = objects[selectedIndex]; o.x = kotlin.math.round(o.x * 2f) / 2f; o.y = kotlin.math.round(o.y * 2f) / 2f; o.z = kotlin.math.round(o.z * 2f) / 2f
    }
    @Synchronized fun exportSceneJson(): String {
        val root = JSONObject()
            .put("format", "modelador3d-map")
            .put("version", 1)
            .put("objects", JSONArray())
        val serialized = root.getJSONArray("objects")
        objects.forEach { object3d ->
            val position = JSONArray().put(object3d.x).put(object3d.y).put(object3d.z)
            val rotation = JSONArray().put(object3d.rx).put(object3d.ry).put(object3d.rz)
            val scale = JSONArray().put(object3d.sx).put(object3d.sy).put(object3d.sz)
            serialized.put(
                JSONObject()
                    .put("name", object3d.name)
                    .put("kind", object3d.kind)
                    .put("position", position)
                    .put("rotation", rotation)
                    .put("scale", scale)
                    .put("texturePath", object3d.texturePath ?: JSONObject.NULL)
                    .put("sourceAsset", object3d.sourceAsset ?: JSONObject.NULL)
            )
        }
        return root.toString(2)
    }
    @Synchronized fun importSceneJson(json: String) {
        val root = JSONObject(json)
        val serialized = root.optJSONArray("objects") ?: JSONArray()
        val restored = mutableListOf<SceneObject>()
        for (index in 0 until serialized.length()) {
            val item = serialized.optJSONObject(index) ?: continue
            val position = item.optJSONArray("position") ?: JSONArray()
            val rotation = item.optJSONArray("rotation") ?: JSONArray()
            val scale = item.optJSONArray("scale") ?: JSONArray()
            restored.add(
                SceneObject(
                    name = item.optString("name", "Objeto_${index + 1}"),
                    kind = item.optString("kind", "Cubo").takeIf { it in SUPPORTED_MESHES } ?: "Cubo",
                    x = position.optDouble(0, 0.0).toFloat(),
                    y = position.optDouble(1, 0.0).toFloat(),
                    z = position.optDouble(2, 0.0).toFloat(),
                    rx = rotation.optDouble(0, 0.0).toFloat(),
                    ry = rotation.optDouble(1, 0.0).toFloat(),
                    rz = rotation.optDouble(2, 0.0).toFloat(),
                    sx = scale.optDouble(0, 1.0).toFloat(),
                    sy = scale.optDouble(1, 1.0).toFloat(),
                    sz = scale.optDouble(2, 1.0).toFloat(),
                    texturePath = optionalString(item, "texturePath"),
                    sourceAsset = optionalString(item, "sourceAsset")
                )
            )
        }
        remember()
        objects.clear()
        objects.addAll(restored)
        objects.mapNotNull { it.sourceAsset }.distinct().forEach { path ->
            MeshImporter.read(File(path))?.let { externalMeshes[path] = it }
        }
        selectedIndex = if (objects.isEmpty()) -1 else 0
    }
    private fun optionalString(item: JSONObject, key: String): String? =
        if (item.isNull(key)) null else item.optString(key, "").ifBlank { null }
    private fun remember() { undoStack.addLast(objects.map { it.copy() }); if (undoStack.size > 50) undoStack.removeFirst(); redoStack.clear() }
    fun orbit(dx: Float, dy: Float) { angleY += dx; angleX = (angleX + dy).coerceIn(-89f, 89f) }
    fun zoom(amount: Float) { distance = (distance + amount).coerceIn(2f, 30f) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.07f, 0.08f, 0.1f, 1f)
        program = GLES30.glCreateProgram().also {
            GLES30.glAttachShader(it, shader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER))
            GLES30.glAttachShader(it, shader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER))
            GLES30.glLinkProgram(it)
        }
        SUPPORTED_MESHES.forEach { kind ->
            val mesh = PrimitiveMeshes.create(kind)
            meshBuffers[kind] = java.nio.ByteBuffer.allocateDirect(mesh.size * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().apply { put(mesh); position(0) }
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 45f, width.toFloat() / height, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        Matrix.setLookAtM(view, 0, 0f, 0f, distance, 0f, 0f, 0f, 0f, 1f, 0f)
        GLES30.glUseProgram(program)
        val objectsToDraw = synchronized(this) { objects.map { it.copy() } }
        val currentTool = synchronized(this) { tool }
        val pos = GLES30.glGetAttribLocation(program, "aPosition"); val color = GLES30.glGetAttribLocation(program, "aColor")
        val tint = GLES30.glGetUniformLocation(program, "uTint")
        objectsToDraw.forEachIndexed { index, object3d ->
            val externalPath = object3d.sourceAsset?.takeIf { externalMeshes.containsKey(it) }
            val vertexBuffer = if (externalPath != null) {
                val key = "external:$externalPath"
                meshBuffers.getOrPut(key) {
                    val mesh = externalMeshes[externalPath]!!
                    java.nio.ByteBuffer.allocateDirect(mesh.size * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().apply { put(mesh); position(0) }
                }
            } else {
                meshBuffers[object3d.kind] ?: meshBuffers["Cubo"]!!
            }
            vertexBuffer.position(0); GLES30.glVertexAttribPointer(pos, 3, GLES30.GL_FLOAT, false, 6 * 4, vertexBuffer); GLES30.glEnableVertexAttribArray(pos)
            vertexBuffer.position(3); GLES30.glVertexAttribPointer(color, 3, GLES30.GL_FLOAT, false, 6 * 4, vertexBuffer); GLES30.glEnableVertexAttribArray(color)
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, object3d.x, object3d.y, object3d.z)
            Matrix.rotateM(model, 0, object3d.rx, 1f, 0f, 0f); Matrix.rotateM(model, 0, object3d.ry, 0f, 1f, 0f); Matrix.rotateM(model, 0, object3d.rz, 0f, 0f, 1f)
            Matrix.scaleM(model, 0, object3d.sx, object3d.sy, object3d.sz)
            Matrix.rotateM(model, 0, angleX, 1f, 0f, 0f); Matrix.rotateM(model, 0, angleY, 0f, 1f, 0f)
            Matrix.multiplyMM(mvp, 0, view, 0, model, 0); Matrix.multiplyMM(mvp, 0, projection, 0, mvp, 0)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uMvp"), 1, false, mvp, 0)
            if (index == selectedIndex) GLES30.glUniform4f(tint, 1.25f, 1.25f, 1.25f, 1f) else GLES30.glUniform4f(tint, 1f, 1f, 1f, 1f)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexBuffer.limit() / 6)
            if (currentTool == "edit" && index == selectedIndex) {
                GLES30.glPointSize(10f)
                GLES30.glDrawArrays(GLES30.GL_POINTS, 0, vertexBuffer.limit() / 6)
            }
        }
    }

    private fun shader(type: Int, source: String): Int = GLES30.glCreateShader(type).also { GLES30.glShaderSource(it, source); GLES30.glCompileShader(it) }
    companion object {
        private val SUPPORTED_MESHES = listOf("Cubo", "Esfera", "Cilindro", "Cone", "Plano", "Toro")
        private const val VERTEX_SHADER = "#version 300 es\nin vec3 aPosition; in vec3 aColor; out vec3 vColor; uniform mat4 uMvp; void main(){gl_Position=uMvp*vec4(aPosition,1.0);vColor=aColor;}"
        private const val FRAGMENT_SHADER = "#version 300 es\nprecision mediump float; in vec3 vColor; uniform vec4 uTint; out vec4 outColor; void main(){outColor=vec4(vColor*uTint.rgb,1.0);}"
        private val CUBE = floatArrayOf(
            -1f,-1f,1f,1f,0.3f,0.3f, 1f,-1f,1f,1f,0.3f,0.3f, 1f,1f,1f,1f,0.3f,0.3f, -1f,-1f,1f,1f,0.3f,0.3f, 1f,1f,1f,1f,0.3f,0.3f, -1f,1f,1f,1f,0.3f,0.3f,
            -1f,-1f,-1f,0.3f,1f,0.4f, -1f,1f,-1f,0.3f,1f,0.4f, 1f,1f,-1f,0.3f,1f,0.4f, -1f,-1f,-1f,0.3f,1f,0.4f, 1f,1f,-1f,0.3f,1f,0.4f, 1f,-1f,-1f,0.3f,1f,0.4f,
            -1f,1f,-1f,0.3f,0.5f,1f, -1f,1f,1f,0.3f,0.5f,1f, 1f,1f,1f,0.3f,0.5f,1f, -1f,1f,-1f,0.3f,0.5f,1f, 1f,1f,1f,0.3f,0.5f,1f, 1f,1f,-1f,0.3f,0.5f,1f,
            -1f,-1f,-1f,1f,0.8f,0.2f, 1f,-1f,-1f,1f,0.8f,0.2f, 1f,-1f,1f,1f,0.8f,0.2f, -1f,-1f,-1f,1f,0.8f,0.2f, 1f,-1f,1f,1f,0.8f,0.2f, -1f,-1f,1f,1f,0.8f,0.2f,
            1f,-1f,-1f,0.8f,0.3f,1f, 1f,1f,-1f,0.8f,0.3f,1f, 1f,1f,1f,0.8f,0.3f,1f, 1f,-1f,-1f,0.8f,0.3f,1f, 1f,1f,1f,0.8f,0.3f,1f, 1f,-1f,1f,0.8f,0.3f,1f,
            -1f,-1f,-1f,0.2f,0.7f,1f, -1f,-1f,1f,0.2f,0.7f,1f, -1f,1f,1f,0.2f,0.7f,1f, -1f,-1f,-1f,0.2f,0.7f,1f, -1f,1f,1f,0.2f,0.7f,1f, -1f,1f,-1f,0.2f,0.7f,1f)
    }
}
