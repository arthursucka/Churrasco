package com.longynus.churrasco

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CreateChurrascoActivity2 : AppCompatActivity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_churrasco2)

        // Recuperar os dados enviados
        val churrascoDate = intent.getStringExtra("churrascoDate") ?: "Data não definida"
        val hora = intent.getStringExtra("hora") ?: "Hora não definida"
        val local = intent.getStringExtra("local") ?: "Local não definido"
        val fornecidos = intent.getStringArrayListExtra("fornecidos") ?: arrayListOf()

        // Exibir os detalhes básicos (data, hora e local)
        findViewById<TextView>(R.id.txtDetalhes).text =
            "Data: $churrascoDate\nHora: $hora\nLocal: $local"

        // Exibir a lista de itens fornecidos
        val fornecidosContainer: LinearLayout = findViewById(R.id.fornecidosContainer)
        fornecidos.forEach { item ->
            val itemView = TextView(this).apply {
                text = "• $item"
                textSize = 16f
                setPadding(8, 8, 8, 8)
            }
            fornecidosContainer.addView(itemView)
        }

        // Mapeia labels de itens fornecidos para comparação
        val fornecidosLabels = fornecidos.map { it }

        // Calcular os itens que não foram fornecidos
        val itensNaoFornecidos = ItemConstants.listaCompletaDeItens.filter { it.label !in fornecidosLabels }

        // Exibir a lista de itens não fornecidos
        val naoFornecidosContainer: LinearLayout = findViewById(R.id.naoFornecidosContainer)
        itensNaoFornecidos.forEach { item ->
            val itemView = TextView(this).apply {
                text = "• ${item.label}"
                textSize = 16f
                setPadding(8, 8, 8, 8)
            }
            naoFornecidosContainer.addView(itemView)
        }

        // Botão para confirmar o churrasco
        findViewById<Button>(R.id.btnConfirmarChurrasco).setOnClickListener {
            sendNotificationToFriends(churrascoDate, hora, local, fornecidos)
        }
    }

    private fun sendNotificationToFriends(
        churrascoDate: String,
        hora: String,
        local: String,
        fornecidos: ArrayList<String>
    ) {
        val topic = "churrasco_updates"
        val title = "Convite para Churrasco!"
        val body = "Novo Churrasco!\nData: $churrascoDate\nHora: $hora\nLocal: $local"

        val json = JSONObject().apply {
            put("topic", topic)
            put("title", title)
            put("body", body)
            put("data", JSONObject().apply {
                put("evento", "Churrasco!")
                put("churrascoDate", churrascoDate)
                put("hora", hora)
                put("local", local)
                put("fornecidos", JSONArray(fornecidos))
            })
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        Thread {
            try {
                val response = client.newCall(
                    Request.Builder()
                        .url("https://fcm-server-oe8u.onrender.com/send-notification")
                        .post(requestBody)
                        .addHeader("Content-Type", "application/json")
                        .build()
                ).execute()

                if (response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "Churrasco confirmado e amigos notificados!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Log.e("Backend", "Erro ao enviar notificação: ${response.code}")
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "Falha ao notificar amigos. Verifique o backend.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("Backend", "Erro ao conectar com o backend: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this, "Erro ao conectar com o servidor.", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }.start()
    }
}