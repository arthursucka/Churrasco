package com.longynus.churrasco

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class EscolherItensActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_escolher_itens)

        // Recuperar os itens não fornecidos
        val itensNaoFornecidos: ArrayList<String?> = intent.getStringArrayListExtra("itensNaoFornecidos") ?: arrayListOf()

        // Verificar se a lista de itens não fornecidos está vazia
        if (itensNaoFornecidos.isEmpty()) {
            Toast.makeText(this, "Nenhum item disponível para seleção.", Toast.LENGTH_SHORT).show()
            finish() // Finaliza a Activity se não houver itens para exibir
            return
        }

        // Container para as checkboxes
        val checkboxContainer: LinearLayout = findViewById(R.id.checkboxContainer)
        val checkboxes = mutableListOf<CheckBox>()

        // Adicionar as checkboxes dinamicamente
        itensNaoFornecidos.forEach { item ->
            val checkBox = CheckBox(this).apply {
                text = item
                textSize = 16f
            }
            checkboxes.add(checkBox)
            checkboxContainer.addView(checkBox)
        }

        // Botão para confirmar a seleção
        val btnConfirmar: Button = findViewById(R.id.btnConfirmar)
        btnConfirmar.setOnClickListener {
            // Filtrar os itens selecionados
            val itensSelecionados = checkboxes.filter { it.isChecked }.map { it.text.toString() }

            // Validar se algum item foi selecionado
            if (itensSelecionados.isEmpty()) {
                Toast.makeText(this, "Por favor, selecione ao menos um item.", Toast.LENGTH_SHORT).show()
            } else {
                // Exibir mensagem com os itens escolhidos (ou enviar para próxima etapa)
                Toast.makeText(this, "Itens escolhidos: ${itensSelecionados.joinToString(", ")}", Toast.LENGTH_SHORT).show()

                // Fechar a activity após a seleção (pode ser alterado para retornar os itens selecionados)
                finish()
            }
        }
    }
}


