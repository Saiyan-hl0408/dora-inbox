package com.halo.expressassistant.api

import android.content.Context
import com.halo.expressassistant.data.ExpressDetail
import com.halo.expressassistant.data.ExpressItem

/** Inherited interface retained; all Kuaidi100 networking removed. */
object KuaiDi100 {
    data class Company(val comCode: String, val name: String)
    suspend fun detectCompany(mailNo: String): Company? = null
    suspend fun fetchDetail(context: Context, item: ExpressItem): ExpressDetail =
        error("个人版已移除快递100，请用已绑定的购物平台查询")
}
