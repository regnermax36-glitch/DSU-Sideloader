package vegabobo.dsusideloader.ui.screen

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import vegabobo.dsusideloader.core.MaxRegnerCore
import vegabobo.dsusideloader.core.SystemImagePorter
import vegabobo.dsusideloader.ui.maxregner.MaxRegnerMainScreen
import vegabobo.dsusideloader.ui.screen.about.AboutScreen
import vegabobo.dsusideloader.ui.screen.adb.AdbScreen
import vegabobo.dsusideloader.ui.screen.libraries.LibrariesScreen
import vegabobo.dsusideloader.ui.screen.settings.Settings
import vegabobo.dsusideloader.util.PrivilegeManager

object Destinations {
    const val Homepage = "home"
    const val Preferences = "preferences"
    const val ADBInstallation = "adb_installation"
    const val About = "about"
    const val Libraries = "libraries"
    const val Up = "up"
}

@Composable
fun Navigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Destinations.Homepage) {
        fun navigate(destination: String) {
            if (destination == Destinations.Up) {
                navController.navigateUp()
            } else {
                navController.navigate(destination)
            }
        }

        composable(Destinations.Homepage) {
            // Use MaxRegner Main Screen instead of original Home
            val maxRegnerCore: MaxRegnerCore = hiltViewModel<MaxRegnerViewModel>().maxRegnerCore
            val systemImagePorter: SystemImagePorter = hiltViewModel<MaxRegnerViewModel>().systemImagePorter
            val privilegeManager: PrivilegeManager = hiltViewModel<MaxRegnerViewModel>().privilegeManager

            MaxRegnerMainScreen(
                maxRegnerCore = maxRegnerCore,
                systemImagePorter = systemImagePorter,
                privilegeManager = privilegeManager,
                onNavigateToSettings = { navigate(Destinations.Preferences) },
                onNavigateToPorting = { /* Navigate to porting screen */ },
            )
        }
        composable(Destinations.Preferences) { Settings(navigate = { navigate(it) }) }
        composable(Destinations.ADBInstallation) { AdbScreen(navigate = { navigate(it) }) }
        composable(Destinations.About) { AboutScreen(navigate = { navigate(it) }) }
        composable(Destinations.Libraries) { LibrariesScreen(navigate = { navigate(it) }) }
    }
}
