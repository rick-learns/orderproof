package dev.orderproof.emberkitchen

import android.app.Application
import dev.orderproof.emberkitchen.candidate.CandidateBuild
import dev.orderproof.emberkitchen.core.AppLog

class EmberKitchenApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AppLog.i("app.start version=${BuildConfig.VERSION_NAME} commit=${BuildConfig.GIT_COMMIT} faulty=${CandidateBuild.IS_FAULTY}")
    }
}
