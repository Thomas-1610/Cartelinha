package com.example.cartelinha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.cartelinha.ui.navigation.AppNavHost
import com.example.cartelinha.ui.theme.CartelinhaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CartelinhaApp).container
        setContent {
            CartelinhaTheme {
                AppNavHost(container)
            }
        }
    }
}
