package com.wco

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class WCOPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(WCO())
    }
}
