package com.halo.expressassistant.hub

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.halo.expressassistant.data.ExpressItem

object DouyinOfficial {
    fun open(activity:Activity,item:ExpressItem) {
        val route=DouyinOrderPayload.waybill(item.jumpLinks,item.queryChannel)
        if(route==null){Toast.makeText(activity,"该订单暂未提供官方物流入口",Toast.LENGTH_LONG).show();return}
        try {activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(route.nativeSchema).buildUpon().scheme("snssdk1128").build()).setPackage("com.ss.android.ugc.aweme"))}
        catch(_:Exception){Toast.makeText(activity,"请在抖音 App 的订单中查看完整物流",Toast.LENGTH_LONG).show()}
    }
}
