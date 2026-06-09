package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.DatabaseRepository
import com.example.ui.KhapulApp
import com.example.ui.KhapulViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize SQLite Room DB & Repository
        val database = AppDatabase.getDatabase(this)
        val repository = DatabaseRepository(database)

        // Instantiate ViewModel with manual Factory
        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(KhapulViewModel::class.java)) {
                    return KhapulViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        })[KhapulViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    KhapulApp(viewModel = viewModel)
                }
            }
        }
    }
}
