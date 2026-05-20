package com.wco

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WCOSettings(
    private val plugin: WCOPlugin,
    private val sharedPref: SharedPreferences
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        TextView(requireContext()).apply {
            text = "WCO Domain Selection"
            textSize = 20f
            setPadding(0, 0, 0, 24)
            layout.addView(this)
        }

        val radioGroup = RadioGroup(requireContext())
        layout.addView(radioGroup)

        TextView(requireContext()).apply {
            text = "Loading domains..."
            textSize = 14f
            layout.addView(this)
        }

        val currentDomain = sharedPref.getString("selected_domain", null) ?: "https://www.wco.tv"

        CoroutineScope(Dispatchers.Main).launch {
            val domains = withContext(Dispatchers.IO) {
                try {
                    plugin.api.fetchDomains()
                } catch (_: Exception) {
                    emptyList()
                }
            }

            radioGroup.removeAllViews()

            if (domains.isEmpty()) {
                (layout.getChildAt(2) as TextView).text = "Could not fetch domains. Using default."
                addDomainRadio(radioGroup, "https://www.wco.tv", currentDomain)
            } else {
                (layout.getChildAt(2) as TextView).text = "Select a domain:"
                for (d in domains) {
                    if (d.status != 200) continue
                    addDomainRadio(radioGroup, d.domain, currentDomain)
                }
            }

            TextView(requireContext()).apply {
                text = "Custom domain"
                textSize = 14f
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                setPadding(0, 16, 0, 0)
                setOnClickListener {
                    val input = android.widget.EditText(requireContext())
                    input.setText(currentDomain)
                    AlertDialog.Builder(requireContext())
                        .setTitle("Enter Custom Domain")
                        .setView(input)
                        .setPositiveButton("Save") { _, _ ->
                            val customDomain = input.text.toString().trim()
                            if (customDomain.isNotEmpty()) {
                                plugin.api.updateDomain(customDomain)
                                dismiss()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                layout.addView(this)
            }
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val radio = radioGroup.findViewById<RadioButton>(checkedId)
            if (radio != null) {
                plugin.api.updateDomain(radio.text.toString())
                dismiss()
            }
        }

        return layout
    }

    private fun addDomainRadio(group: RadioGroup, domain: String, current: String) {
        RadioButton(group.context).apply {
            text = domain
            isChecked = domain == current
            setPadding(0, 8, 0, 8)
            group.addView(this)
        }
    }
}
