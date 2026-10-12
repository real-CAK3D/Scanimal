package com.realcak3d.scanimal

import android.app.Application
import com.realcak3d.scanimal.work.CareWorker

class ScanimalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CareWorker.createChannel(this)
        CareWorker.schedule(this)
    }
}
