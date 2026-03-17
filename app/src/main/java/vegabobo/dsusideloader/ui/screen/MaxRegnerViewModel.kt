package vegabobo.dsusideloader.ui.screen

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import vegabobo.dsusideloader.core.MaxRegnerCore
import vegabobo.dsusideloader.core.SystemImagePorter
import vegabobo.dsusideloader.util.PrivilegeManager
import javax.inject.Inject

@HiltViewModel
class MaxRegnerViewModel @Inject constructor(
    val maxRegnerCore: MaxRegnerCore,
    val systemImagePorter: SystemImagePorter,
    val privilegeManager: PrivilegeManager
) : ViewModel() {
    
    init {
        // Initialize components
        maxRegnerCore.initialize(null)
        systemImagePorter.initialize()
    }
    
    override fun onCleared() {
        super.onCleared()
        maxRegnerCore.cleanup()
        systemImagePorter.cleanup()
        privilegeManager.cleanup()
    }
}

