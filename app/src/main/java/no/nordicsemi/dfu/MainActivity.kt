package no.nordicsemi.dfu

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import no.nordicsemi.android.material.you.NordicActivity
import no.nordicsemi.android.material.you.NordicTheme
import no.nordicsemi.android.navigation.NavigationView
import no.nordicsemi.dfu.profile.DFUDestinations
import no.nordicsemi.dfu.storage.DeepLinkHandler
import no.nordicsemi.ui.scanner.ScannerDestinations
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : NordicActivity() {

    @Inject
    lateinit var linkHandler: DeepLinkHandler

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isDeepLink = linkHandler.handleDeepLink(intent)
        viewModel.logEvent(isDeepLink)

        setContent {
            NordicTheme {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxSize()
                ) {
                    NavigationView(DFUDestinations + ScannerDestinations)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        linkHandler.handleDeepLink(intent)
    }
}
