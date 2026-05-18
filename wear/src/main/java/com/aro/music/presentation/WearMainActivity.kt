package com.aro.music.presentation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.wear.ambient.AmbientLifecycleObserver
import com.aro.music.data.WearLifecycleState
import com.aro.music.presentation.theme.WearAroTheme
import com.aro.music.presentation.viewmodel.WearPlayerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WearMainActivity : FragmentActivity() {

    companion object {
        val isForeground: Boolean
            get() = WearLifecycleState.isForeground.value
    }

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            WearLifecycleState.setAmbient(true)
        }

        override fun onExitAmbient() {
            WearLifecycleState.setAmbient(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AmbientLifecycleObserver(this, ambientCallback).also {
            lifecycle.addObserver(it)
        }

        setContent {
            val playerViewModel: WearPlayerViewModel = hiltViewModel()
            val albumArt by playerViewModel.albumArt.collectAsState()
            val paletteSeedArgb by playerViewModel.paletteSeedArgb.collectAsState()
            val themePalette by playerViewModel.themePalette.collectAsState()

            WearAroTheme(
                albumArt = albumArt,
                seedColorArgb = paletteSeedArgb,
                themePalette = themePalette,
            ) {
                WearNavigation()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        WearLifecycleState.setForeground(true)
    }

    override fun onStop() {
        WearLifecycleState.setForeground(false)
        super.onStop()
    }
}
