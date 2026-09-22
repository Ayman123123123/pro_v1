package com.red.sovereign

import android.app.Application
import com.red.sovereign.core.auth.IdentityManager
import com.red.sovereign.features.calls.RedVoipMaster
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * RED Sovereign Application
 * Entry point for the RED messaging app.
 */
@HiltAndroidApp
class RedSovereignApp : Application() {

    @Inject lateinit var voipMaster: RedVoipMaster
    @Inject lateinit var identityManager: IdentityManager

    override fun onCreate() {
        super.onCreate()
        // RED Sovereign initialization
        instance = this
    }

    companion object {
        lateinit var instance: RedSovereignApp
            private set
    }
}
