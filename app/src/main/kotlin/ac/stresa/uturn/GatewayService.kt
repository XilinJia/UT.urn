package ac.stresa.uturn

import ac.mdiq.podcini.sources.IFeedSearchProvider
import ac.mdiq.podcini.sources.IPodciniGateway
import ac.mdiq.podcini.sources.Provider
import ac.stresa.uturn.core.Localization.Companion.getPreferredContentCountry
import ac.stresa.uturn.core.Localization.Companion.getPreferredLocalization
import ac.stresa.uturn.core.UTurnProvider
import ac.stresa.uturn.core.VistaDownloaderImpl
import ac.stresa.uturn.core.VistaDownloaderImpl.Companion.RECAPTCHA_COOKIES_KEY
import ac.stresa.uturn.core.VistaDownloaderImpl.Companion.YOUTUBE_RESTRICTED_MODE_COOKIE_KEY
import ac.stresa.uturn.core.VistaGuideSearcher
import ac.stresa.uturn.core.util.InfoCache
import ac.stresa.uturn.core.util.potoken.PoTokenProviderImpl
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import kotlin.collections.set

class GatewayService : Service() {
    private val searchProviderBinder = VistaGuideSearcher()
    private val uturnProviderBinder = UTurnProvider()
    private val gatewayBinder = object : IPodciniGateway.Stub() {
        override fun getSearchProvider(): IFeedSearchProvider {
            return searchProviderBinder
        }
        override fun getProvider(): Provider {
            return uturnProviderBinder
        }
    }
    override fun onCreate() {
        Log.e("GatewayService", "onCreate")
        init()
        Log.e("GatewayService", "searchProviderBinder=$searchProviderBinder")
        Log.e("GatewayService", "uturnProviderBinder=$uturnProviderBinder")
    }

    private fun init() {
        val vistaDownloader = VistaDownloaderImpl.init()
        vistaDownloader.mCookies[RECAPTCHA_COOKIES_KEY] = ""
        vistaDownloader.mCookies.remove(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY)
        //        vistaDownloader.mCookies[RECAPTCHA_COOKIES_KEY] = appPrefs.recaptcha_cookies
        //        if (appPrefs.restrictedModeEnabled) vistaDownloader.mCookies[YOUTUBE_RESTRICTED_MODE_COOKIE_KEY] = YOUTUBE_RESTRICTED_MODE_COOKIE
        //        else vistaDownloader.mCookies.remove(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY)
        InfoCache.instance.clearCache()
        NewPipe.init(vistaDownloader, getPreferredLocalization(), getPreferredContentCountry())
        for (s in ServiceList.all()) {
            if (s.serviceId == ServiceList.PeerTube.serviceId) {
                //                not doing anything now
            }
        }
        YoutubeStreamExtractor.setPoTokenProvider(PoTokenProviderImpl)
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.d("GatewayService", "onBind: ${intent.action}")
        return gatewayBinder
    }
}