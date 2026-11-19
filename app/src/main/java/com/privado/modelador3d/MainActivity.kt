package com.privado.modelador3d

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.InputType

class MainActivity : Activity() {
    private lateinit var viewport: ModelerSurfaceView
    private lateinit var status: TextView
    private lateinit var objectCount: TextView
    private val transformFields = mutableListOf<EditText>()
    private var updatingTransform = false
    private val pickAssetRequest = 4101
    private val saveMapRequest = 4102
    private val loadMapRequest = 4103
    private var pendingMapJson: String? = null
    private val accent = Color.rgb(91, 137, 255)
    private val surface = Color.rgb(29, 32, 40)
    private val panel = Color.rgb(23, 26, 32)
    private val text = Color.rgb(225, 229, 238)
    private val muted = Color.rgb(145, 153, 170)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN, android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        viewport = ModelerSurfaceView(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(panel) }
        root.addView(topBar(), LinearLayout.LayoutParams(-1, dp(54)))
        val workspace = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        workspace.addView(toolRail(), LinearLayout.LayoutParams(dp(68), -1))
        workspace.addView(viewport, LinearLayout.LayoutParams(0, -1, 1f))
        workspace.addView(propertiesPanel(), LinearLayout.LayoutParams(dp(244), -1))
        root.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(timeline(), LinearLayout.LayoutParams(-1, dp(62)))
        root.addView(statusBar(), LinearLayout.LayoutParams(-1, dp(25)))
        viewport.onSceneChanged = { updateCount(); refreshTransformFields() }
        setContentView(root)
        refreshTransformFields()
    }

    private fun topBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), 0, dp(10), 0); background = bg(surface, 0)
        addView(label("MODELADOR 3D", 15f, text, true), LinearLayout.LayoutParams(dp(150), -1))
        addView(separator(), LinearLayout.LayoutParams(dp(1), dp(28)))
        addView(iconButton("＋", "Novo", false) { viewport.clearScene(); updateCount() })
        addView(iconButton("▣", "Abrir", false) { showMapMenu(openOnly = true) })
        addView(iconButton("▤", "Salvar", false) { saveMap() })
        addView(iconButton("⬡", "Mapa", false) { showMapMenu() })
        addView(iconButton("⇩", "Importar asset", false) { showImportMenu() })
        addView(separator(), LinearLayout.LayoutParams(dp(1), dp(28)))
        addView(label("Objeto", 13f, muted, false), LinearLayout.LayoutParams(dp(72), -1))
        addView(iconButton("⧉", "Duplicar", false) { viewport.duplicateSelected(); updateCount(); setStatus("Objeto duplicado") })
        addView(iconButton("⌫", "Excluir", false) { viewport.deleteSelected(); updateCount(); setStatus("Objeto excluído") })
        addView(iconButton("↶", "Desfazer", false) { viewport.undo(); updateCount(); setStatus("Desfazer") })
        addView(iconButton("↷", "Refazer", false) { viewport.redo(); updateCount(); setStatus("Refazer") })
        val spacer = Space(this@MainActivity); addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        addView(iconButton("◎", "IA", true) { showAiDialog() })
        addView(iconButton("⚙", "Configurações", false) { setStatus("Configurações do aplicativo") })
    }

    private fun toolRail(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; setPadding(dp(6), dp(10), dp(6), dp(6)); background = bg(surface, 0)
        addTool(this, "⌖", "Selecionar", "select"); addTool(this, "✣", "Mover", "move"); addTool(this, "⟳", "Girar", "rotate"); addTool(this, "↗", "Escalar", "scale")
        addView(separator(), LinearLayout.LayoutParams(dp(42), dp(1)).apply { setMargins(0, dp(10), 0, dp(10)) })
        addTool(this, "＋", "Adicionar", "add"); addTool(this, "▣", "Extrudar", "extrude"); addTool(this, "⌁", "Bevel", "bevel"); addTool(this, "◇", "Loop cut", "loop")
        val spacer = Space(this@MainActivity); addView(spacer, LinearLayout.LayoutParams(1, 0, 1f)); addTool(this, "▥", "Modo edição", "edit")
    }

    private fun addTool(parent: LinearLayout, glyph: String, title: String, tool: String) {
        parent.addView(iconButton(glyph, title, tool == "select") { if (tool == "add") showAddMenu() else { viewport.setTool(tool); setStatus(title) } }, LinearLayout.LayoutParams(-1, dp(52)))
    }

    private fun propertiesPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), 0); background = bg(panel, 0)
        addView(label("CENA", 11f, muted, true), LinearLayout.LayoutParams(-1, dp(26)))
        objectCount = label("●  Cubo", 14f, text, false); addView(objectCount, LinearLayout.LayoutParams(-1, dp(34)))
        addView(separator(), LinearLayout.LayoutParams(-1, dp(1))); addView(label("TRANSFORMAÇÃO", 11f, muted, true), LinearLayout.LayoutParams(-1, dp(38)))
        addTransformRow(this, "Posição", 0); addTransformRow(this, "Rotação", 1); addTransformRow(this, "Escala", 2)
        addView(separator(), LinearLayout.LayoutParams(-1, dp(18))); addView(label("MATERIAL", 11f, muted, true), LinearLayout.LayoutParams(-1, dp(28)))
        addView(TextView(this@MainActivity).apply { text = "  ▰  Material padrão\n      Base color     #5B89FF"; setTextColor(this@MainActivity.text); textSize = 13f; setPadding(dp(6), dp(8), 0, dp(8)); background = bg(Color.rgb(34, 38, 48), 8) }, LinearLayout.LayoutParams(-1, dp(68)))
        val fill = Space(this@MainActivity); addView(fill, LinearLayout.LayoutParams(1, 0, 1f)); addView(label("OUTLINER", 11f, muted, true), LinearLayout.LayoutParams(-1, dp(28)))
        addView(label("▾  Coleção\n    ◇  Cubo", 13f, text, false), LinearLayout.LayoutParams(-1, dp(56)))
    }

    private fun addTransformRow(parent: LinearLayout, name: String, component: Int) {
        parent.addView(label(name, 12f, muted, false), LinearLayout.LayoutParams(-1, dp(22)))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(3) { axis ->
            val field = EditText(this).apply {
                textSize = 11f; setTextColor(this@MainActivity.text); setSingleLine(true); gravity = Gravity.CENTER; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED; background = bg(Color.rgb(34, 38, 48), 5)
                setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) commitTransformFields() }
            }
            transformFields.add(field); row.addView(field, LinearLayout.LayoutParams(0, dp(30), 1f).apply { setMargins(if (axis == 0) 0 else dp(4), 0, 0, 0) })
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, dp(30)).apply { setMargins(0, 0, 0, dp(6)) })
    }

    private fun timeline(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), 0, dp(10), 0); background = bg(surface, 0)
        addView(iconButton("▶", "Reproduzir", false) { setStatus("Reproduzindo animação") }); addView(iconButton("■", "Parar", false) { setStatus("Animação parada") })
        addView(label("  1 / 250", 12f, muted, false), LinearLayout.LayoutParams(dp(90), -1)); addView(label("│   │   │   │   │   │   │   │   │   │   │", 12f, muted, false), LinearLayout.LayoutParams(0, -1, 1f))
        addView(iconButton("◆", "Adicionar keyframe", true) { setStatus("Keyframe adicionado no frame atual") })
    }

    private fun statusBar(): View = TextView(this).apply { text = "  Viewport  |  Coleção  |  Cubo  |  OpenGL ES 3.0 nativo"; setTextColor(muted); textSize = 10f; gravity = Gravity.CENTER_VERTICAL; background = bg(Color.rgb(15, 17, 22), 0) }
    private fun iconButton(glyph: String, title: String, active: Boolean, action: () -> Unit): TextView = TextView(this).apply { text = glyph; contentDescription = title; gravity = Gravity.CENTER; textSize = if (active) 22f else 19f; setTextColor(if (active) Color.WHITE else muted); typeface = Typeface.DEFAULT_BOLD; background = bg(if (active) accent else Color.TRANSPARENT, 7); setOnClickListener { action() }; layoutParams = LinearLayout.LayoutParams(dp(48), dp(42)).apply { setMargins(dp(2), 0, dp(2), 0) } }
    private fun separator(): View = View(this).apply { setBackgroundColor(Color.rgb(54, 59, 70)) }
    private fun label(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply { text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL; if (bold) typeface = Typeface.DEFAULT_BOLD }
    private fun bg(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); if (radius > 0) cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun setStatus(value: String) { status.text = "  $value" }
    private fun updateCount() { objectCount.text = "●  ${viewport.selectedName() ?: "Cena vazia"}  (${viewport.objectCount()})" }
    private fun refreshTransformFields() {
        if (!::viewport.isInitialized || transformFields.size != 9) return
        val values = viewport.selectedTransform(); updatingTransform = true
        transformFields.forEachIndexed { index, field -> field.setText("%.2f".format(java.util.Locale.US, values[index])) }
        updatingTransform = false
    }
    private fun commitTransformFields() {
        if (updatingTransform || transformFields.size != 9) return
        transformFields.forEachIndexed { index, field -> field.text.toString().toFloatOrNull()?.let { viewport.setTransform(index / 3, index % 3, it) } }
    }

    private fun showAddMenu() {
        val items = arrayOf("Cubo", "Esfera", "Cilindro", "Cone", "Plano", "Toro")
        AlertDialog.Builder(this).setTitle("Adicionar objeto").setItems(items) { _, which -> viewport.addPrimitive(items[which]); updateCount(); setStatus("${items[which]} adicionado") }.show()
    }

    private fun showImportMenu() {
        val items = arrayOf("Modelo 3D", "Textura / imagem", "Mapa / projeto")
        AlertDialog.Builder(this).setTitle("Importar para a cena").setItems(items) { _, which ->
            when (which) {
                0 -> pickFiles(arrayOf("model/gltf-binary", "model/gltf+json", "model/obj", "model/stl", "model/*"), pickAssetRequest)
                1 -> pickFiles(arrayOf("image/png", "image/jpeg", "image/webp", "image/*"), pickAssetRequest)
                else -> pickFiles(arrayOf("application/json", "application/octet-stream", "*/*"), loadMapRequest)
            }
        }.show()
    }

    private fun showMapMenu(openOnly: Boolean = false) {
        if (openOnly) {
            pickFiles(arrayOf("application/json", "application/octet-stream", "*/*"), loadMapRequest)
            return
        }
        val items = arrayOf("Novo mapa", "Salvar mapa/projeto", "Abrir mapa/projeto")
        AlertDialog.Builder(this).setTitle("Mapa e projeto").setItems(items) { _, which ->
            when (which) {
                0 -> { viewport.clearScene(); updateCount(); setStatus("Novo mapa criado") }
                1 -> saveMap()
                2 -> pickFiles(arrayOf("application/json", "application/octet-stream", "*/*"), loadMapRequest)
            }
        }.show()
    }

    private fun saveMap() {
        pendingMapJson = viewport.exportSceneJson()
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "mapa.m3dmap")
            addCategory(Intent.CATEGORY_OPENABLE)
        }, saveMapRequest)
    }

    private fun pickFiles(types: Array<String>, request: Int) {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; putExtra(Intent.EXTRA_MIME_TYPES, types); addCategory(Intent.CATEGORY_OPENABLE) }, request)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (requestCode) {
            saveMapRequest -> pendingMapJson?.let { contentResolver.openOutputStream(uri)?.use { stream -> stream.write(it.toByteArray()) }; pendingMapJson = null; setStatus("Mapa salvo") }
            loadMapRequest -> runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader -> viewport.importSceneJson(reader.readText()) }
            }.onSuccess {
                updateCount(); setStatus("Mapa aberto")
            }.onFailure { error -> setStatus("Falha ao abrir mapa: ${error.message ?: "arquivo inválido"}") }
            pickAssetRequest -> importAsset(uri)
        }
    }

    private fun importAsset(uri: Uri) {
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "asset_importado"
        val lower = name.lowercase()
        val isTexture = lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
        val imports = java.io.File(filesDir, "imports").apply { mkdirs() }
        val target = java.io.File(imports, name.replace(Regex("[^a-zA-Z0-9._-]"), "_"))
        val copied = runCatching {
            contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        }.isSuccess
        if (!copied) {
            setStatus("Falha ao copiar o arquivo: $name")
            return
        }
        if (isTexture) { viewport.assignTexture(target.absolutePath); setStatus("Textura importada: $name") }
        else {
            viewport.addImportedAsset(name, target.absolutePath)
            updateCount()
            val extension = lower.substringAfterLast('.', "")
            val mode = if (extension in setOf("obj", "stl", "ply")) "malha carregada" else "arquivo registrado; parser 3D pendente"
            setStatus("Modelo importado: $name ($mode)")
        }
    }

    private fun showAiDialog() {
        val config = AiConfigStore(this).read(); val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 8, 30, 0) }
        val provider = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Groq", "OpenAI", "Anthropic", "Gemini", "OpenRouter", "Personalizado")); setSelection(config.providerIndex) }
        val baseUrl = EditText(this).apply { hint = "URL base compatível com OpenAI"; setText(config.baseUrl) }; val model = EditText(this).apply { hint = "Modelo"; setText(config.model) }; val key = EditText(this).apply { hint = "Chave de API"; inputType = 0x81; setText(config.apiKey) }
        form.addView(provider); form.addView(baseUrl); form.addView(model); form.addView(key)
        AlertDialog.Builder(this).setTitle("IA do Modelador").setMessage("A chave é protegida pelo Android Keystore.").setView(form).setNegativeButton("Cancelar", null).setPositiveButton("Salvar") { _, _ -> AiConfigStore(this).save(AiConfig(provider.selectedItemPosition, baseUrl.text.toString(), model.text.toString(), key.text.toString())); setStatus("IA configurada: ${provider.selectedItem}") }.show()
    }
}
