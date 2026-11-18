package com.privado.modelador3d

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var viewport: ModelerSurfaceView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 18, 22))
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 6, 8, 6)
            setBackgroundColor(Color.rgb(29, 32, 38))
        }
        fun button(label: String, action: () -> Unit): Button = Button(this).apply {
            text = label
            setOnClickListener { action() }
            minHeight = 0
            minimumHeight = 0
        }
        viewport = ModelerSurfaceView(this)
        status = TextView(this).apply {
            text = "Viewport nativo | toque/mouse: orbitar | roda/dois dedos: zoom | Delete: excluir"
            setTextColor(Color.LTGRAY)
            setPadding(12, 5, 12, 5)
            textSize = 12f
        }
        toolbar.addView(button("+ Cubo") { viewport.addCube(); status.text = "Cubo criado" })
        toolbar.addView(button("Duplicar") { viewport.duplicateSelected(); status.text = "Objeto duplicado" })
        toolbar.addView(button("Excluir") { viewport.deleteSelected(); status.text = "Objeto excluído" })
        toolbar.addView(button("IA") { showAiDialog() })
        root.addView(toolbar, LinearLayout.LayoutParams(-1, 54))
        root.addView(viewport, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(status, LinearLayout.LayoutParams(-1, 32))
        setContentView(root)
    }

    private fun showAiDialog() {
        val config = AiConfigStore(this).read()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 8, 30, 0)
        }
        val provider = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Groq", "OpenAI", "Anthropic", "Gemini", "OpenRouter", "Personalizado"))
            setSelection(config.providerIndex)
        }
        val baseUrl = EditText(this).apply { hint = "URL base (ex.: https://api.groq.com/openai/v1)"; setText(config.baseUrl) }
        val model = EditText(this).apply { hint = "Modelo"; setText(config.model) }
        val key = EditText(this).apply { hint = "Chave de API"; inputType = 0x81; setText(config.apiKey) }
        form.addView(provider); form.addView(baseUrl); form.addView(model); form.addView(key)
        AlertDialog.Builder(this)
            .setTitle("Configuração da IA")
            .setMessage("A chave é protegida pelo Android Keystore neste aparelho.")
            .setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                AiConfigStore(this).save(AiConfig(provider.selectedItemPosition, baseUrl.text.toString(), model.text.toString(), key.text.toString()))
                status.text = "Configuração de IA salva; adaptador ${provider.selectedItem} pronto para conexão"
            }.show()
    }
}
