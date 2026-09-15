package com.b2binventory.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.b2binventory.app.theme.B2BInventoryTheme
import com.b2binventory.app.ui.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            B2BInventoryTheme {
                AppNavigation()
            }
        }
    }
}
