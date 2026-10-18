package com.mcqstudio.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.mcqstudio.pro.ui.MCQStudioScreen
import com.mcqstudio.pro.ui.MCQStudioViewModel
import com.mcqstudio.pro.ui.theme.MCQStudioProTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MCQStudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MCQStudioProTheme {
                MCQStudioScreen(viewModel = viewModel)
            }
        }
    }
}
