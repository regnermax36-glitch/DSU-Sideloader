package vegabobo.dsusideloader

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import vegabobo.dsusideloader.core.SystemImagePorter
import vegabobo.dsusideloader.model.SystemPortingOptions
import vegabobo.dsusideloader.ui.maxregner.SystemPortingScreen
import vegabobo.dsusideloader.ui.theme.MaxRegnerTheme
import javax.inject.Inject

@AndroidEntryPoint
class SystemPortingActivity : ComponentActivity() {
    
    @Inject
    lateinit var systemImagePorter: SystemImagePorter
    
    private var selectedImageUri by mutableStateOf<Uri?>(null)
    
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri = uri
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            MaxRegnerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SystemPortingScreen(
                        systemImagePorter = systemImagePorter,
                        selectedImageUri = selectedImageUri,
                        onSelectImage = { imagePickerLauncher.launch("*/*") },
                        onStartPorting = { uri, options -> startPorting(uri, options) },
                        onBack = { finish() }
                    )
                }
            }
        }
    }
    
    private fun startPorting(uri: Uri, options: SystemPortingOptions) {
        // Start system image porting process
        // This would typically be handled by a ViewModel
    }
}
