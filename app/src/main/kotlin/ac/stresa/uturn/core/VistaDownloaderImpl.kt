package ac.stresa.uturn.core

import ac.mdiq.podcini.shared.USER_AGENT
import ac.mdiq.podcini.shared.PodciniHttpClient.getKtorClient
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpMethod
import io.ktor.http.headers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

class VistaDownloaderImpl private constructor() : Downloader() {
    val mCookies: MutableMap<String, String> = mutableMapOf()

    override fun execute(request: Request): Response = runBlocking(Dispatchers.IO) { executeSuspend(request) }

    private suspend fun executeSuspend(request: Request): Response {
        val url = request.url()
        val headers = request.headers().toMutableMap()
        val dataToSend = request.dataToSend()
        val youtubeCookie = if (url.contains(YOUTUBE_DOMAIN)) mCookies[YOUTUBE_RESTRICTED_MODE_COOKIE_KEY] else null
        val cookies = listOfNotNull(youtubeCookie, mCookies[RECAPTCHA_COOKIES_KEY])
            .flatMap { it.split("; *".toRegex()).filter { it1-> it1.isNotEmpty() } }
            .distinct()
            .joinToString("; ")

        val response = getKtorClient().request(url) {
            method = HttpMethod.parse(request.httpMethod())
            if (dataToSend != null) setBody(dataToSend)
            headers {
                append("User-Agent", USER_AGENT)
                if (cookies.isNotEmpty()) append("Cookie", cookies)
                headers.forEach { (name, values) ->
                    remove(name)
                    values.forEach { append(name, it) }
                }
            }
        }
        if (response.status.value == 429) throw ReCaptchaException("reCaptcha Challenge requested", url)
        return Response(response.status.value, response.status.description, response.headers.entries().associate { it.key to it.value }, response.bodyAsText(), response.request.url.toString())
    }

    companion object {
        private const val TAG = "VistaDownloaderImpl"

        const val YOUTUBE_RESTRICTED_MODE_COOKIE_KEY: String = "youtube_restricted_mode_key"
        const val YOUTUBE_RESTRICTED_MODE_COOKIE: String = "PREF=f2=8000000"
        const val YOUTUBE_DOMAIN: String = "youtube.com"
        const val RECAPTCHA_COOKIES_KEY: String = "recaptcha_cookies"
        
        var instance: VistaDownloaderImpl? = null
            private set

        /**
         * It's recommended to call exactly once in the entire lifetime of the application.
         * @return a new instance of [VistaDownloaderImpl]
         */
        fun init(): VistaDownloaderImpl {
            if (instance == null) instance = VistaDownloaderImpl()
            return instance!!
        }
    }
}
