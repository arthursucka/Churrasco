package com.longynus.churrasco

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity


class ChurrascoDetailsActivity : AppCompatActivity() {

    @SuppressLint("SetTextI18n", "LongLogTag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_churrasco_details)

        // Recuperar os dados da intent
        val churrascoDate: String = intent.getStringExtra("churrascoDate") ?: "Data não definida"
        val hora: String = intent.getStringExtra("hora") ?: "Hora não definida"
        val local: String = intent.getStringExtra("local") ?: "Local não definido"
        val evento: String = intent.getStringExtra("evento") ?: "Evento não especificado"
        val fornecidos = intent.getStringArrayListExtra("fornecidos") ?: arrayListOf()
        val itensNaoFornecidos = intent.getStringArrayListExtra("itensNaoFornecidos") ?: arrayListOf()

        Log.d("ChurrascoDetailsActivity", "Fornecidos recebidos: $fornecidos")

        // Atualizar os TextViews com os detalhes do churrasco
        findViewById<TextView>(R.id.txtDetalhesEvento).text = """
            Evento: $evento
            Data: $churrascoDate
            Hora: $hora
            Local: $local            
        """.trimIndent()

        // Atualizar o contêiner com os itens fornecidos
        val fornecidosContainer: LinearLayout = findViewById(R.id.fornecidosContainer)
        fornecidosContainer.removeAllViews() // Limpar para evitar duplicação

        if (fornecidos.isEmpty()) {
            val noItemsView = TextView(this).apply {
                text = "Nenhum item fornecido."
                textSize = 16f
                setPadding(8, 8, 8, 8)
            }
            fornecidosContainer.addView(noItemsView)
        } else {
            fornecidos.forEach { item ->
                val itemView = TextView(this).apply {
                    text = "• $item"
                    textSize = 16f
                    setPadding(8, 8, 8, 8)
                }
                fornecidosContainer.addView(itemView)
            }
        }

        // Configurar botão "Aceitar"
        val btnAceitar: Button = findViewById(R.id.btnAceitar)
        btnAceitar.setOnClickListener {
            // Criar uma lista de itens disponíveis para o convidado escolher
            val intent = Intent(this, EscolherItensActivity::class.java).apply {
                putStringArrayListExtra("itensNaoFornecidos", itensNaoFornecidos)
            }
            startActivity(intent)
        }

        // Configurar botão "Declinar"
        findViewById<Button>(R.id.btnDeclinar).setOnClickListener {
            Toast.makeText(this, "Convite declinado.", Toast.LENGTH_SHORT).show()
            finish() // Fecha a tela
        }
    }
}
