package com.wco

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class WCOPlugin : Plugin() {
    lateinit var api: WCO

    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("wco_prefs", Context.MODE_PRIVATE)
        val savedDomain = sharedPref.getString("selected_domain", null)
        api = WCO(savedDomain, sharedPref)
        registerMainAPI(api)

        val activity = context as? AppCompatActivity
        openSettings = {
            val frag = WCOSettings(this, sharedPref)
            activity?.supportFragmentManager?.let { fm ->
                frag.show(fm, "WCOSettings")
            }
        }
    }
}
