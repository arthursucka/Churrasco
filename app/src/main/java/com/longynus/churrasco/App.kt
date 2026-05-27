package com.longynus.churrasco

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(applicationContext)
    }
}
