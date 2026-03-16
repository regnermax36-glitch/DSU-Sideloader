package vegabobo.dsusideloader

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import dagger.hilt.android.HiltAndroidApp
import vegabobo.dsusideloader.core.SystemImagePorter
import vegabobo.dsusideloader.core.MaxRegnerCore
import javax.inject.Inject

@HiltAndroidApp
class MaxRegnerApplication : Application() {

    @Inject
    lateinit var systemImagePorter: SystemImagePorter
    
    @Inject
    lateinit var maxRegnerCore: MaxRegnerCore

    companion object {
        lateinit var instance: MaxRegnerApplication
            private set
        
        val isSystemPorting = mutableStateOf(false)
        val currentRomName = mutableStateOf("MaxRegner Custom ROM")
        val portingProgress = mutableStateOf(0f)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize MaxRegner Core Systems
        maxRegnerCore.initialize(this)
        systemImagePorter.initialize()
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // Enable hidden API access for system.img manipulation
        try {
            val hiddenApiBypass = Class.forName("org.lsposed.hiddenapibypass.HiddenApiBypass")
            val addHiddenApiExemptions = hiddenApiBypass.getMethod("addHiddenApiExemptions", String::class.java)
            addHiddenApiExemptions.invoke(null, "")
        } catch (e: Exception) {
            // Fallback for devices without HiddenApiBypass
        }
    }
}

