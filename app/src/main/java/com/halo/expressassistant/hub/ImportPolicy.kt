package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import java.util.UUID

/** Pure reconciliation for user-selected text imports; never clears known logistics fields. */
object ImportPolicy {
    enum class Outcome { Added, Updated, AlreadyExists, NoInfo, ParseError }
    data class Result(
        val outcome:Outcome,
        val item:ExpressItem?=null,
        val localItems:List<ExpressItem> = emptyList()
    ) {
        val changed:Boolean get()=outcome==Outcome.Added || outcome==Outcome.Updated
    }
    private val waybill=Regex("[A-Za-z0-9]{8,32}")
    private val phone=Regex("1[3-9][0-9]{9}")
    private val shelfCode=Regex("[A-Za-z0-9]+(?:-[A-Za-z0-9]+){1,4}")
    private fun sameMail(a:String,b:String)=a.equals(b,true)

    fun plan(
        text:String,
        companyName:String,
        localItems:List<ExpressItem>,
        platformItems:List<ExpressItem> = emptyList(),
        now:Long=System.currentTimeMillis(),
        newId:()->String={UUID.randomUUID().toString()},
        parser:(String)->PickupText.Parsed=PickupText::parse
    ):Result {
        val input=text.trim()
        if(input.isEmpty())return Result(Outcome.NoInfo,localItems=localItems)
        val parsed=try { parser(input) } catch(_:Exception) {
            return Result(Outcome.ParseError,localItems=localItems)
        }
        val bare=input.replace(Regex("[—–－_]"),"-")
        val bareMail=bare.takeIf {
            waybill.matches(it) && !phone.matches(it) &&
                !it.startsWith("ORDER",true) && !it.startsWith("LOCAL",true)
        }.orEmpty()
        val bareCode=bare.takeIf {
            (it.matches(Regex("[0-9]{2,6}")) || shelfCode.matches(it)) &&
                it.length in 2..24 && it.any(Char::isDigit)
        }.orEmpty()
        val mail=parsed.waybill.ifBlank {bareMail}
        val codes=parsed.codes.ifEmpty {if(mail.isBlank() && bareCode.isNotBlank())listOf(bareCode) else emptyList()}
        if(mail.isBlank() && codes.isEmpty())return Result(Outcome.NoInfo,localItems=localItems)
        val code=codes.distinct().joinToString("、")
        val localIndex=if(mail.isNotBlank())localItems.indexOfFirst {sameMail(it.mailNo,mail)}
            else localItems.indexOfFirst {
                it.mailNo.startsWith("LOCAL-") && it.pickupCode==code &&
                    (it.provider==parsed.platform || parsed.platform=="未指定平台")
            }
        val oldLocal=localItems.getOrNull(localIndex)
        val platform=if(mail.isBlank())null else platformItems.firstOrNull {!it.isOrderNumber && sameMail(it.mailNo,mail)}
        val old=platform?.let { fresh ->
            val base=oldLocal?.let {SyncCacheMerge.merge(it,fresh)} ?: fresh
            base.copy(
                id=oldLocal?.id ?: fresh.id,
                pickupCode=oldLocal?.pickupCode?.ifBlank {fresh.pickupCode} ?: fresh.pickupCode,
                pickupCodeSource=if(oldLocal?.pickupCode?.isNotBlank()==true)oldLocal.pickupCodeSource else fresh.pickupCodeSource,
                pickupStation=base.pickupStation.ifBlank {oldLocal?.pickupStation.orEmpty()},
                pickupAddress=base.pickupAddress.ifBlank {oldLocal?.pickupAddress.orEmpty()},
                recipientPhoneTail=base.recipientPhoneTail.ifBlank {oldLocal?.recipientPhoneTail.orEmpty()}
            )
        } ?: oldLocal
        if(old!=null && (code.isBlank() || code==old.pickupCode)) {
            return Result(Outcome.AlreadyExists,old,localItems)
        }
        val item=if(old!=null) {
            old.copy(
                pickupCode=code.ifBlank {old.pickupCode},
                pickupCodeSource=if(code.isNotBlank())"local" else old.pickupCodeSource,
                stateName=if(old.source=="local" && old.stateName=="等待取件码" && code.isNotBlank())"待取件" else old.stateName,
                observedAt=now,notifiedText=input,notifiedTime=now.toString()
            )
        } else {
            ExpressItem(
                id=newId(),companyCode="",companyName=companyName,
                mailNo=mail.ifBlank {"LOCAL-${newId()}"},
                latestText=input,stateName=if(code.isBlank())"等待取件码" else "待取件",
                source="local",provider=parsed.platform,pickupCode=code,
                pickupCodeSource=if(code.isNotBlank())"local" else "",
                observedAt=now,notifiedText=input,notifiedTime=now.toString()
            )
        }
        val updated=localItems.toMutableList()
        if(localIndex>=0)updated[localIndex]=item else updated.add(0,item)
        return Result(if(old==null)Outcome.Added else Outcome.Updated,item,updated)
    }
}
