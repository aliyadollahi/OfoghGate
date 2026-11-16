package com.example

import android.app.Application
import com.example.data.local.CallRepository
import com.example.data.local.EncryptedConfigStore
import com.example.data.local.SoftphoneDatabase
import com.example.engine.DiagnosticLogger
import com.example.engine.KnockAlarmScheduler
import com.example.engine.LinphoneSipManager
import com.example.engine.NetworkDetector
import com.example.engine.PortKnocker

class IssabelGateApp : Application() {

    lateinit var logger: DiagnosticLogger
        private set
    lateinit var configStore: EncryptedConfigStore
        private set
    lateinit var database: SoftphoneDatabase
        private set
    lateinit var callRepository: CallRepository
        private set
    lateinit var portKnocker: PortKnocker
        private set
    lateinit var networkDetector: NetworkDetector
        private set
    lateinit var knockScheduler: KnockAlarmScheduler
        private set
    lateinit var sipManager: LinphoneSipManager
        private set

    override fun onCreate() {
        super.onCreate()
        logger = DiagnosticLogger()
        configStore = EncryptedConfigStore(this)
        database = SoftphoneDatabase.getInstance(this)
        callRepository = CallRepository(
            database.callHistoryDao(),
            database.quickExtensionDao()
        )
        portKnocker = PortKnocker(logger)
        networkDetector = NetworkDetector(this, portKnocker, logger)
        knockScheduler = KnockAlarmScheduler(this, logger)
        sipManager = LinphoneSipManager(this, logger)
    }
}
