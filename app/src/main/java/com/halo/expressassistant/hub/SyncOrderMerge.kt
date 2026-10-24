package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem

/** A PDD order survives an erroneous or shared waybill until its own page is verified. */
object SyncOrderMerge {
    fun key(item:ExpressItem)=if(item.source=="pdd")"pdd|${item.accountId}|${item.id}" else "parcel|${item.mailNo}"
    fun combine(old:List<ExpressItem>,current:List<ExpressItem>,fresh:List<ExpressItem>,onNumberChange:(ExpressItem,ExpressItem)->Unit={_,_->}):List<ExpressItem> {
        val result=linkedMapOf<String,ExpressItem>()
        (old+current).forEach {result[key(it)]=it}
        for(item in fresh) {
            val previous=old.firstOrNull {it.source==item.source && it.accountId==item.accountId && it.id==item.id}
            val updated=if(item.isOrderNumber && previous!=null && !previous.isOrderNumber && !previous.pddReadRepairRequired)
                item.copy(mailNo=previous.mailNo,companyName=item.companyName.ifBlank {previous.companyName},isOrderNumber=false) else item
            if(previous!=null && previous.mailNo!=updated.mailNo) {
                if(result[key(previous)]?.let {it.source==previous.source && it.accountId==previous.accountId && it.id==previous.id}==true)result.remove(key(previous))
                onNumberChange(previous,updated)
            }
            result[key(updated)]=updated
        }
        return result.values.toList()
    }
}
