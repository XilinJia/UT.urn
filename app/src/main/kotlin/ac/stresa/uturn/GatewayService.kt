package ac.stresa.uturn

import ac.mdiq.podcini.shared.PROVIDER_API_VERSION
import ac.mdiq.podcini.shared.ProviderAttrs
import ac.mdiq.podcini.shared.ShareType
import ac.mdiq.podcini.sources.IFeedSearchProvider
import ac.mdiq.podcini.sources.IPodciniGateway
import ac.mdiq.podcini.sources.Provider
import ac.stresa.uturn.core.FeedBuilder.Companion.FEEDTYPE
import ac.stresa.uturn.core.Localization.Companion.getPreferredContentCountry
import ac.stresa.uturn.core.Localization.Companion.getPreferredLocalization
import ac.stresa.uturn.core.UTurnProvider
import ac.stresa.uturn.core.DownloaderImpl
import ac.stresa.uturn.core.DownloaderImpl.Companion.RECAPTCHA_COOKIES_KEY
import ac.stresa.uturn.core.DownloaderImpl.Companion.YOUTUBE_RESTRICTED_MODE_COOKIE_KEY
import ac.stresa.uturn.core.UTurnSearcher
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
    private val searchProviderBinder = UTurnSearcher()
    private val uturnProviderBinder = UTurnProvider()
    private val gatewayBinder = object : IPodciniGateway.Stub() {
        override fun getAttributes(): ProviderAttrs {
            return ProviderAttrs(
                name = "UT.urn",
                apiVersion = PROVIDER_API_VERSION,
                feedType = FEEDTYPE,
                hasMultiQualities = true,
                hasSeparateAVs = true,
                supportDonwload = false,
                hasViewCount = true,
                hasLikeCount = true,
                shareLogType = ShareType.YTMedia.name)
        }
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
        val downloader = DownloaderImpl.init()
        downloader.mCookies[RECAPTCHA_COOKIES_KEY] = ""
        downloader.mCookies.remove(YOUTUBE_RESTRICTED_MODE_COOKIE_KEY)
        InfoCache.instance.clearCache()
        NewPipe.init(downloader, getPreferredLocalization(), getPreferredContentCountry())
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