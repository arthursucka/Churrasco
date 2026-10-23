package com.longynus.churrasco

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.LOCAL_BACKEND) useLocalFirebase()
    }

    // Projeto "demo-" dos emuladores: o backend em CHURRASCO_LOCAL_TEST só aceita esse.
    private fun useLocalFirebase() {
        FirebaseApp.initializeApp(
            this,
            FirebaseOptions.Builder()
                .setProjectId(LOCAL_PROJECT)
                .setApplicationId("1:000000000000:android:0000000000000000")
                .setApiKey("demo-api-key")
                .setDatabaseUrl("https://$LOCAL_PROJECT.firebaseio.com")
                .build()
        )
        FirebaseAuth.getInstance().useEmulator(EMULATOR_HOST, 9099)
        FirebaseDatabase.getInstance().useEmulator(EMULATOR_HOST, 9000)
    }

    private companion object {
        const val LOCAL_PROJECT = "demo-churrasco"
        const val EMULATOR_HOST = "10.0.2.2"
    }
}
