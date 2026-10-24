package com.longynus.churrasco

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.longynus.churrasco.data.ChurrascoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Acorda o servidor já na abertura: se ele estiver dormindo, a espera começa agora.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { ChurrascoRepository.wakeUp() }

        val logo = findViewById<View>(R.id.imgSplashLogo)
        logo.scaleX = 0.96f
        logo.scaleY = 0.96f
        logo.animate().scaleX(1f).scaleY(1f).setDuration(420).start()

        Handler(Looper.getMainLooper()).postDelayed({
            // Primeira abertura, sem ninguém logado: a apresentação vem antes da entrada.
            val first = !OnboardingActivity.seen(this) && !AuthSession.isReady(this)
            startActivity(Intent(this, if (first) OnboardingActivity::class.java else LoginActivity::class.java))
            finish()
        }, 720)
    }
}
