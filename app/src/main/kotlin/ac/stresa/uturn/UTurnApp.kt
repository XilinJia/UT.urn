package ac.stresa.uturn

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log

class UTurnApp : Application() {
    override fun onCreate() {
        super.onCreate()
        UTurnApp = this
        Log.d("YTApp", "YTApp onCreate")
    }

    companion object {
        private lateinit var UTurnApp: UTurnApp

        fun getApp(): UTurnApp = UTurnApp

        fun getAppContext(): Context = UTurnApp.applicationContext

        fun forceRestart() {
            val intent = Intent(UTurnApp, MainActivity::class.java)
            val mainIntent = Intent.makeRestartActivityTask(intent.component)
            UTurnApp.startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }
    }
}
