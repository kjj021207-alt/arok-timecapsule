package com.arok.timecapsule

import android.app.Application
import android.util.Log
import com.arok.timecapsule.car.CarSignals
import com.arok.timecapsule.car.DemoCarSignals
import com.arok.timecapsule.car.pleos.PleosCarSignals
import com.arok.timecapsule.data.CapsuleRepository
import com.arok.timecapsule.data.db.AppDatabase
import org.osmdroid.config.Configuration

class TimeCapsuleApp : Application() {

    lateinit var repository: CapsuleRepository
        private set
    lateinit var car: CarSignals
        private set

    override fun onCreate() {
        super.onCreate()
        // OpenStreetMap 타일 서버 정책: 앱 식별용 User-Agent 필수
        Configuration.getInstance().userAgentValue = packageName

        repository = CapsuleRepository(AppDatabase.create(this).capsuleDao())

        // Pleos 차량(에뮬레이터)이면 실제 SDK, 아니면 데모 모드
        car = runCatching { PleosCarSignals.createIfAvailable(this) }
            .onFailure { Log.w("TimeCapsuleApp", "Pleos SDK unavailable, using demo mode", it) }
            .getOrNull() ?: DemoCarSignals()
        Log.i("TimeCapsuleApp", "car signals: ${car::class.simpleName}")
    }
}
