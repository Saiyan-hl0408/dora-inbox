package com.halo.expressassistant.hub

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout

/** Public repository contact; original private contact assets are excluded. */
internal object AboutAuthorSection {
    const val GITHUB="https://github.com/Saiyan-hl0408"
    fun add(activity:Activity,parent:LinearLayout) {
        val ui=HubSheets(activity)
        ui.card(parent).apply {
            addView(ui.text("Saiyan-hl0408",15,true))
            ui.fullButton(this,"源码与商业授权联系") {
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(GITHUB))) }
            }
        }
    }
}
