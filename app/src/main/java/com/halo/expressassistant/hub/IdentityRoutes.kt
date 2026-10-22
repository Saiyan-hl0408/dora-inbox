package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import java.net.URLEncoder

/** Destination links verified in upstream source; JD link provided and opened by the user. */
object IdentityRoutes {
    const val JD_URL="https://convenient-h5.jd.com/identityCode?channel=xueshengjirukou"
    const val TB_URL="https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code?x-ssr=true"
    const val PDD_URL="https://m.pinduoduo.net/mdkd/package?entry_source=18&extra_params=from_wx_jump%3D1&p_channel=0"
    const val CN_URL="https://market.m.taobao.com/app/cn-yz/multi-activity/authCode.html?bizEntry=ALIPAY_GUOGUO"
    data class Route(val key:String,val title:String,val packageName:String,val webUrl:String,val appUris:List<String>)
    private fun enc(s:String)=URLEncoder.encode(s,"UTF-8").replace("+","%20")
    fun route(key:String):Route=when(key) {
        "jd"->Route(key,"京东身份码","com.jingdong.app.mall",JD_URL,listOf("openapp.jdmobile://virtual?params="+enc("""{"category":"jump","des":"m","url":"$JD_URL"}"""),"openjd://virtual?params="+enc("""{"category":"jump","des":"m","url":"$JD_URL"}""")))
        "taobao"->Route(key,"菜鸟身份码","com.taobao.taobao",TB_URL,listOf("taobao://m.taobao.com/tbopen/index.html?h5Url="+enc(TB_URL),"tbopen://m.taobao.com/tbopen/index.html?h5Url="+enc(TB_URL)))
        "pdd"->Route(key,"拼多多身份码","com.xunmeng.pinduoduo",PDD_URL,listOf("pinduoduo://com.xunmeng.pinduoduo/mdkd/package?tab=ID_CODE&entry_source=11&refer_page_name=login&refer_page_sn=10169","pinduoduo://com.xunmeng.pinduoduo/mdkd/package?tab=ID_CODE&entry_source=11"))
        else->Route("cainiao","菜鸟身份码","com.cainiao.wireless",CN_URL,listOf("cainiao://desktop/station_code"))
    }
    fun forParcel(item:ExpressItem):String = PickupUiPolicy.identity(item)
}
