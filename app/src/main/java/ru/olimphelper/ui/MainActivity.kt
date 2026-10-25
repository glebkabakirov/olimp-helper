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
import androidx.compose.runtime.mutableStateOf
import ru.olimphelper.work.Notifier

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val startTab = mutableIntStateOf(0)
    /** Олимпиада, которую надо открыть (нажатие на строку виджета). */
    private val openOlympiad = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readIntent(intent)
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(this)) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            OlimpTheme {
                AppScreen(vm, startTab, openOlympiad)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        intent ?: return
        if (intent.hasExtra(Notifier.EXTRA_TAB)) startTab.intValue = intent.getIntExtra(Notifier.EXTRA_TAB, 0)
        intent.getStringExtra(EXTRA_OLYMPIAD)?.let { openOlympiad.value = it }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    companion object {
        const val EXTRA_OLYMPIAD = "olympiad"
    }
}
