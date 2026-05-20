package com.kimcartoon2

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Kimcartoon2Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Kimcartoon2())
    }
}
