package ac.stresa.uturn

import ac.roma.npeconnector.DownloaderImpl
import ac.roma.npeconnector.DownloaderImpl.Companion.RECAPTCHA_COOKIES_KEY
import ac.roma.npeconnector.DownloaderImpl.Companion.YOUTUBE_RESTRICTED_MODE_COOKIE_KEY
import ac.roma.npeconnector.FeedSearcher
import ac.roma.npeconnector.InfoCache
import ac.roma.npeconnector.Localization.Companion.getPreferredContentCountry
import ac.roma.npeconnector.Localization.Companion.getPreferredLocalization
import ac.roma.npeconnector.MediaSearcher
import ac.stresa.uturn.ui.theme.PodciniProviderTheme
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.schabi.newpipe.extractor.NewPipe

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PodciniProviderTheme {
                Box(modifier = Modifier.background(Color.Black).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("UT.urn Installed", color = Color.Green)
                }
            }
        }

//        val downloader = DownloaderImpl.init()
//        downloader.mCookies[RECAPTCHA_COOKIES_KEY] = ""
//        downloader.mCookies.remove(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY)
//        InfoCache.instance.clearCache()
//        NewPipe.init(downloader, getPreferredLocalization(), getPreferredContentCountry())
//
//        val feedSearcher = FeedSearcher("UT.urn", 0)
//        val fList = feedSearcher.search("The James Bolt Show")
//        Log.d("MainActivity", "found media: ${fList.size }")
//        for (f in fList) Log.d("MainActivity", "${f.title} ${f.feedUrl}")
//
//        val mediaSearcher = MediaSearcher("UT.urn", 0)
//        val elist = mediaSearcher.search("Android 17 Beta", 200)
//        Log.d("MainActivity", "found media: ${elist.size }")
//        for (e in elist) Log.d("MainActivity", "${e.title}")
    }
}
