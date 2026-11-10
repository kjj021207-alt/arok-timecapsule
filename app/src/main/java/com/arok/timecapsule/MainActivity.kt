package com.arok.timecapsule

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arok.timecapsule.ui.MainScreen
import com.arok.timecapsule.ui.MainViewModel
import com.arok.timecapsule.ui.theme.TimeCapsuleTheme

class MainActivity : ComponentActivity() {

    private val app get() = application as TimeCapsuleApp

    // FusedLocation 권한은 보호 수준이 dangerous라 실행 중에 요청해야 한다. 허용되면 차량 연결을 다시 시작한다.
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) app.car.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissions.launch(
            arrayOf(
                PLEOS_FUSED_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.RECORD_AUDIO,
            )
        )

        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(app.repository, app.car) as T
        }
        setContent {
            TimeCapsuleTheme {
                MainScreen(viewModel(factory = factory))
            }
        }
    }

    companion object {
        const val PLEOS_FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
    }
}
