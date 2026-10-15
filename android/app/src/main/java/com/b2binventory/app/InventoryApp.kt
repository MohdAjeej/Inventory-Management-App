package com.b2binventory.app

import android.app.Application
import com.b2binventory.app.di.AppContainer

class InventoryApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        com.b2binventory.app.ui.i18n.I18n.init(this)
    }
}
