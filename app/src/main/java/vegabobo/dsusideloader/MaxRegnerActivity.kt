package vegabobo.dsusideloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.AndroidEntryPoint
import vegabobo.dsusideloader.core.MaxRegnerCore
import vegabobo.dsusideloader.core.SystemImagePorter
import vegabobo.dsusideloader.ui.maxregner.MaxRegnerMainScreen
import vegabobo.dsusideloader.ui.theme.MaxRegnerTheme
import vegabobo.dsusideloader.util.PrivilegeManager
import javax.inject.Inject

@AndroidEntryPoint
class MaxRegnerActivity : ComponentActivity() {

    @Inject
    lateinit var maxRegnerCore: MaxRegnerCore
    
    @Inject
    lateinit var systemImagePorter: SystemImagePorter
    
    @Inject
    lateinit var privilegeManager: PrivilegeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Initialize Shell for root operations
        Shell.getShell { }
        
        setContent {
            MaxRegnerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MaxRegnerMainScreen(
                        maxRegnerCore = maxRegnerCore,
                        systemImagePorter = systemImagePorter,
                        privilegeManager = privilegeManager,
                        onNavigateToSettings = { navigateToSettings() },
                        onNavigateToPorting = { navigateToPorting() }
                    )
                }
            }
        }
        
        // Setup privilege management
        if (savedInstanceState == null) {
            privilegeManager.setupPrivileges(this)
        }
    }
    
    private fun navigateToSettings() {
        // Navigate to MaxRegner Settings
        startActivity(Intent(this, MaxRegnerSettingsActivity::class.java))
    }
    
    private fun navigateToPorting() {
        // Navigate to System Image Porting
        startActivity(Intent(this, SystemPortingActivity::class.java))
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            privilegeManager.cleanup()
        }
    }
}
