package com.winter.mypass

import android.app.Application
import android.util.Log

class App: Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("myPass", "onCreate: Hello!")
    }
}