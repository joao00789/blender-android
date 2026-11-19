package com.privado.modelador3d

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var viewport: ModelerSurfaceView
    private lateinit var status: TextView
    private lateinit var objectCount: TextView
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
        setContentView(root)
    }

    private fun topBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), 0, dp(10), 0); background = bg(surface, 0)
        addView(label("MODELADOR 3D", 15f, text, true), LinearLayout.LayoutParams(dp(150), -1))
        addView(separator(), LinearLayout.LayoutParams(dp(1), dp(28)))
        addView(iconButton("＋", "Novo", false) { viewport.clearScene(); updateCount() })
        addView(iconButton("▣", "Abrir", false) { setStatus("Abrir projeto será conectado ao sistema de arquivos") })
        addView(iconButton("▤", "Salvar", false) { setStatus("Salvar projeto será conectado na próxima etapa") })
        addView(separator(), LinearLayout.LayoutParams(dp(1), dp(28)))
        addView(label("Objeto", 13f, muted, false), LinearLayout.LayoutParams(dp(72), -1))
        addView(iconButton("↶", "Desfazer", false) { setStatus("Desfazer") })
        addView(iconButton("↷", "Refazer", false) { setStatus("Refazer") })
        val spacer = Space(this); addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        addView(iconButton("◎", "IA", true) { showAiDialog() })
        addView(iconButton("⚙", "Configurações", false) { setStatus("Configurações do aplicativo") })
    }

    private fun toolRail(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; setPadding(dp(6), dp(10), dp(6), dp(6)); background = bg(surface, 0)
        addTool(this, "⌖", "Selecionar", "select"); addTool(this, "✣", "Mover", "move"); addTool(this, "⟳", "Girar", "rotate"); addTool(this, "↗", "Escalar", "scale")
        addView(separator(), LinearLayout.LayoutParams(dp(42), dp(1)).apply { setMargins(0, dp(10), 0, dp(10)) })
        addTool(this, "＋", "Adicionar", "add"); addTool(this, "▣", "Extrudar", "extrude"); addTool(this, "⌁", "Bevel", "bevel"); addTool(this, "◇", "Loop cut", "loop")
        val spacer = Space(this); addView(spacer, LinearLayout.LayoutParams(1, 0, 1f)); addTool(this, "▥", "Modo edição", "edit")
    }

    private fun addTool(parent: LinearLayout, glyph: String, title: String, tool: String) {
        parent.addView(iconButton(glyph, title, tool == "select") { if (tool == "add") showAddMenu() else { viewport.setTool(tool); setStatus(title) } }, LinearLayout.LayoutParams(-1, dp(52)))
    }

    private fun propertiesPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), 0); background = bg(panel, 0)
        addView(label("CENA", 11f, muted, true), LinearLayout.LayoutParams(-1, dp(26)))
        objectCount = label("●  Cubo", 14f, text, false); addView(objectCount, LinearLayout.LayoutParams(-1, dp(34)))
        addView(separator(), LinearLayout.LayoutParams(-1, dp(1))); addView(label("TRANSFORMAÇÃO", 11f, muted, true), LinearLayout.LayoutParams(-1, dp(38)))
        addTransformRow(this, "Posição", "0.00     0.00     0.00"); addTransformRow(this, "Rotação", "0.00°    0.00°    0.00°"); addTransformRow(this, "Escala", "1.00     1.00     1.00")
        addView(separator(), LinearLayout.LayoutParams(-1, dp(18))); addView(label("MATERIAL", 11f, muted, true), LinearLayout.LayoutParams(-1, dp(28)))
        addView(TextView(this@MainActivity).apply { text = "  ▰  Material padrão\n      Base color     #5B89FF"; setTextColor(this@MainActivity.text); textSize = 13f; setPadding(dp(6), dp(8), 0, dp(8)); background = bg(Color.rgb(34, 38, 48), 8) }, LinearLayout.LayoutParams(-1, dp(68)))
        val fill = Space(this); addView(fill, LinearLayout.LayoutParams(1, 0, 1f)); addView(label("OUTLINER", 11f, muted, true), LinearLayout.LayoutParams(-1, dp(28)))
        addView(label("▾  Coleção\n    ◇  Cubo", 13f, text, false), LinearLayout.LayoutParams(-1, dp(56)))
    }

    private fun addTransformRow(parent: LinearLayout, name: String, values: String) {
        parent.addView(label(name, 12f, muted, false), LinearLayout.LayoutParams(-1, dp(22)))
        parent.addView(label(values, 12f, text, false).apply { background = bg(Color.rgb(34, 38, 48), 5); setPadding(dp(7), 0, 0, 0); gravity = Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(-1, dp(30)).apply { setMargins(0, 0, 0, dp(6)) })
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
    private fun updateCount() { objectCount.text = "●  Cubo" }

    private fun showAddMenu() {
        val items = arrayOf("Cubo", "Esfera", "Cilindro", "Cone", "Plano", "Toro")
        AlertDialog.Builder(this).setTitle("Adicionar objeto").setItems(items) { _, which -> viewport.addCube(); updateCount(); setStatus("${items[which]} adicionado") }.show()
    }

    private fun showAiDialog() {
        val config = AiConfigStore(this).read(); val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 8, 30, 0) }
        val provider = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Groq", "OpenAI", "Anthropic", "Gemini", "OpenRouter", "Personalizado")); setSelection(config.providerIndex) }
        val baseUrl = EditText(this).apply { hint = "URL base compatível com OpenAI"; setText(config.baseUrl) }; val model = EditText(this).apply { hint = "Modelo"; setText(config.model) }; val key = EditText(this).apply { hint = "Chave de API"; inputType = 0x81; setText(config.apiKey) }
        form.addView(provider); form.addView(baseUrl); form.addView(model); form.addView(key)
        AlertDialog.Builder(this).setTitle("IA do Modelador").setMessage("A chave é protegida pelo Android Keystore.").setView(form).setNegativeButton("Cancelar", null).setPositiveButton("Salvar") { _, _ -> AiConfigStore(this).save(AiConfig(provider.selectedItemPosition, baseUrl.text.toString(), model.text.toString(), key.text.toString())); setStatus("IA configurada: ${provider.selectedItem}") }.show()
    }
}
