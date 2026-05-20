package com.kimcartoon2

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class Kimcartoon2Plugin : BasePlugin() {
    override fun load() {
        registerMainAPI(Kimcartoon2())
    }
}
