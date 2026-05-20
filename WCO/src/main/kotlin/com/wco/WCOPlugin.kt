package com.wco

import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.extractors.FilemoonV2
import com.lagradost.cloudstream3.extractors.Krakenfiles
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class WCOPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(WCO())

        // StreamWish
        registerExtractorAPI(Cdnwish())
        registerExtractorAPI(Multimovies())
        registerExtractorAPI(Asnwish())

        // VidStack
        registerExtractorAPI(Cloudy())
        registerExtractorAPI(vidcloudupns())

        // VidHide
        registerExtractorAPI(Animezia())
        registerExtractorAPI(vidhidevip())
        registerExtractorAPI(Dhtpre())

        // FileSim / FileMoon
        registerExtractorAPI(FileMoonNL())
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(FilemoonV2())

        // VidMoly
        registerExtractorAPI(Vidmolynet())

        // DoodStream
        registerExtractorAPI(D000d())

        // StreamSB
        registerExtractorAPI(StreamSB8())

        // GDMirrorbot
        registerExtractorAPI(GDMirrorbot())
        registerExtractorAPI(Techinmind())

        // StreamRuby
        registerExtractorAPI(Streamruby())
        registerExtractorAPI(StreamRuby())

        // AWSStream
        registerExtractorAPI(AWSStream())
        registerExtractorAPI(Zephyrflick())
        registerExtractorAPI(ascdn21())

        // Animedekhoco
        registerExtractorAPI(Animedekhoco())

        // Blakiteapi
        registerExtractorAPI(Blakiteapi())

        // Abyass
        registerExtractorAPI(Abyass())

        // Built-in CS3 extractors
        registerExtractorAPI(Krakenfiles())
        registerExtractorAPI(Voe())
        registerExtractorAPI(StreamTape())
    }
}
