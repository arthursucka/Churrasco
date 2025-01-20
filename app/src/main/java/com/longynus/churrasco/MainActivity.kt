package com.longynus.churrasco

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FCM"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Solicitar permissão para enviar notificações (necessário para Android 13 ou superior)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        // Obter o token do FCM
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Falha ao obter o token", task.exception)
                    return@OnCompleteListener
                }

                // Obtenha o token
                val token = task.result
                Log.d(TAG, "Token do FCM: $token")

                // Enviar o token ao backend
                sendTokenToBackend(token)
            })

        // Referências aos botões definidos no layout
        val createChurrascoButton = findViewById<Button>(R.id.btnCriarChurrasco)
        val joinChurrascoButton = findViewById<Button>(R.id.btnEntrarChurrasco)

        // Navegar para a tela "Criar Churrasco"
        createChurrascoButton.setOnClickListener {
            val intent = Intent(this, CreateChurrascoActivity1::class.java)
            startActivity(intent)
        }

        // Navegar para a tela "Entrar em Churrasco"
        joinChurrascoButton.setOnClickListener {
            val intent = Intent(this, JoinChurrascoActivity::class.java)
            startActivity(intent)
        }

        // Inscrição no tópico
        subscribeToChurrascoUpdates()
    }

    private fun subscribeToChurrascoUpdates() {
        FirebaseMessaging.getInstance().subscribeToTopic("churrasco_updates")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FCM", "Inscrição no tópico churrasco_updates bem-sucedida")
                    Toast.makeText(this, "Inscrito no tópico churrasco_updates", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("FCM", "Falha ao inscrever no tópico churrasco_updates", task.exception)
                    Toast.makeText(this, "Falha ao se inscrever no tópico", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun sendTokenToBackend(token: String) {
        val url = "https://fcm-server-oe8u.onrender.com/send-notification" // Substitua pelo endpoint correto do seu backend

        // Criar o JSON do corpo da requisição
        val json = JSONObject().apply {
            put("topic", "churrasco_updates") // Opcional, ajuste conforme necessário
            put("token", token)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

        // Configurar o cliente HTTP com timeout
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        // Configurar a requisição HTTP
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        // Executar a requisição em uma thread separada
        Thread {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.d(TAG, "Token enviado com sucesso ao backend")
                } else {
                    Log.e(TAG, "Erro ao enviar token ao backend: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao conectar com o backend: ${e.message}")
            }
        }.start()
    }
}