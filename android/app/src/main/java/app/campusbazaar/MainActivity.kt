package app.campusbazaar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.campusbazaar.ui.AppRoot
import app.campusbazaar.ui.theme.CB
import app.campusbazaar.ui.theme.CampusBazaarTheme
import app.campusbazaar.util.Notifier
import androidx.compose.ui.graphics.toArgb

class MainActivity : ComponentActivity() {
    private var pendingPeer by mutableStateOf<String?>(null)
    private var pendingActivity by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(CB.Paper.toArgb(), CB.Ink.toArgb()),
            navigationBarStyle = SystemBarStyle.light(CB.Surface.toArgb(), CB.Ink.toArgb()),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readIntent(intent)
        val container = (application as CampusBazaarApp).container
        setContent {
            CampusBazaarTheme {
                AppRoot(
                    container = container,
                    pendingPeer = pendingPeer,
                    pendingActivity = pendingActivity,
                    onPendingHandled = { pendingPeer = null; pendingActivity = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    /** Taps on in-app-generated notifications open the right chat or the Activity inbox. */
    private fun readIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(Notifier.EXTRA_PEER)?.let { pendingPeer = it }
        if (intent.getBooleanExtra(Notifier.EXTRA_ACTIVITY, false)) pendingActivity = true
    }
}
