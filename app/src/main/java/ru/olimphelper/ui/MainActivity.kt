package ru.olimphelper.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import ru.olimphelper.work.Notifier

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val startTab = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startTab.intValue = intent.getIntExtra(Notifier.EXTRA_TAB, 0)
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(this)) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            OlimpTheme {
                AppScreen(vm, startTab)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startTab.intValue = intent.getIntExtra(Notifier.EXTRA_TAB, startTab.intValue)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }
}
