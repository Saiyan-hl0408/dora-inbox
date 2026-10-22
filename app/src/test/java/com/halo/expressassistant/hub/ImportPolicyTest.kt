// Synthetic phone-shaped rejection fixtures only; not an account or contact.
package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test

class ImportPolicyTest {
    private fun item(code:String="")=ExpressItem(
        id="stable-local-id",companyCode="jd",companyName="京东快递",mailNo="JD123456789012",
        latestText="已到校园服务站",latestTime="2026-10-20 10:30",pickupCode=code,
        pickupStation="校园服务站",pickupAddress="一楼",recipientPhoneTail="1234",
        originalName="抽纸",source="jd",accountId="account-a",stateName="待取件"
    )
    private fun plan(text:String,local:List<ExpressItem> = emptyList(),platform:List<ExpressItem> = emptyList()) =
        ImportPolicy.plan(text,"手动导入",local,platform,now=1000L,newId={"new-id"})

    @Test fun completeWaybillAloneAndPickupCodeAloneAreUsefulInputs() {
        val mail=plan("JD123456789012")
        assertEquals(ImportPolicy.Outcome.Added,mail.outcome)
        assertEquals("JD123456789012",mail.item?.mailNo)
        assertEquals("",mail.item?.pickupCode)
        val code=plan("取件码654321")
        assertEquals(ImportPolicy.Outcome.Added,code.outcome)
        assertEquals("654321",code.item?.pickupCode)
        assertTrue(code.item!!.mailNo.startsWith("LOCAL-"))
        assertEquals("",code.item?.latestTime)
        assertEquals(1000L,code.item?.observedAt)
    }
    @Test fun explicitWaybillLabelDoesNotRequireAnAdditionalParcelContextWord() {
        assertEquals("YT123456789012",plan("运单号：YT123456789012").item?.mailNo)
        assertEquals("A1-2-3456",plan("A1－2—3456").item?.pickupCode)
    }
    @Test fun duplicateCodeOnlyImportIsNotReportedAsInvalidOrGivenAnotherId() {
        val first=plan("取件码654321")
        val again=plan("自提码654321",first.localItems)
        assertEquals(ImportPolicy.Outcome.AlreadyExists,again.outcome)
        assertEquals(first.item?.id,again.item?.id)
        assertEquals(1,again.localItems.size)
        assertFalse(again.changed)
    }
    @Test fun existingPlatformWaybillIsRecognizedWithoutCreatingLocalDuplicate() {
        val existing=item("2-3-4567")
        val result=plan("jd123456789012",platform=listOf(existing))
        assertEquals(ImportPolicy.Outcome.AlreadyExists,result.outcome)
        assertEquals("2-3-4567",result.item?.pickupCode)
        assertTrue(result.localItems.isEmpty())
    }
    @Test fun addingManualCodeToPlatformParcelKeepsItsOtherFields() {
        val existing=item()
        val result=plan("运单号JD123456789012，取件码2-3-4567",platform=listOf(existing))
        assertEquals(ImportPolicy.Outcome.Updated,result.outcome)
        assertEquals("2-3-4567",result.item?.pickupCode)
        assertEquals("local",result.item?.pickupCodeSource)
        assertEquals(existing.id,result.item?.id)
        assertEquals(existing.latestText,result.item?.latestText)
        assertEquals(existing.latestTime,result.item?.latestTime)
        assertEquals(existing.pickupStation,result.item?.pickupStation)
        assertEquals(existing.pickupAddress,result.item?.pickupAddress)
        assertEquals(existing.accountId,result.item?.accountId)
        assertEquals(existing.originalName,result.item?.originalName)
        assertEquals("jd",result.item?.source)
    }
    @Test fun updatingExistingLocalCodePreservesItsIdAndDoesNotLoseTrace() {
        val existing=item("old-code").copy(source="local")
        val result=plan("运单号JD123456789012，取件码3-4-5678",listOf(existing))
        assertEquals(ImportPolicy.Outcome.Updated,result.outcome)
        assertEquals("stable-local-id",result.item?.id)
        assertEquals("3-4-5678",result.item?.pickupCode)
        assertEquals(existing.latestText,result.item?.latestText)
        assertEquals(existing.latestTime,result.item?.latestTime)
        val waybillOnly=plan("运单号JD123456789012",result.localItems)
        assertEquals(ImportPolicy.Outcome.AlreadyExists,waybillOnly.outcome)
        assertEquals("3-4-5678",waybillOnly.item?.pickupCode)
    }
    @Test fun platformRefreshAndLocalCodeTogetherKeepStableLocalIdentity() {
        val local=item("2-3-4567").copy(source="local")
        val platform=item().copy(id="platform-id",pickupAddress="新取件处")
        val result=plan("运单号jd123456789012，取件码3-4-5678",listOf(local),listOf(platform))
        assertEquals("stable-local-id",result.item?.id)
        assertEquals("新取件处",result.item?.pickupAddress)
        assertEquals(1,result.localItems.size)
    }
    @Test fun unrelatedTextMaskedCodePhoneAndOrderIdAreNotParcels() {
        for(text in listOf("验证码为123456，五分钟内有效","取件码12****","19900000000","订单号123456789012")) {
            assertEquals(text,ImportPolicy.Outcome.NoInfo,plan(text).outcome)
        }
    }
    @Test fun parserFailureIsSeparateFromNoUsefulInformationAndDoesNotChangeCache() {
        val existing=listOf(item())
        val result=ImportPolicy.plan("包裹","手动",existing,parser={throw IllegalArgumentException("bad input")})
        assertEquals(ImportPolicy.Outcome.ParseError,result.outcome)
        assertEquals(existing,result.localItems)
        assertFalse(result.changed)
    }
}
