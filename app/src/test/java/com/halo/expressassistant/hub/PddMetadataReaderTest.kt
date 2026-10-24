package com.halo.expressassistant.hub

import com.halo.expressassistant.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PddMetadataReaderTest {
    private val item=ExpressItem("owned-order","PDD","申通快递","TEST0000012345",source="pdd",accountId="owner",
        logisticsStatus="已签收",latestText="已签收",latestTime="2026-10-24 14:59:18",logisticsVersion=1,state=3)
    private val signed=DetailPoint("已签收，签收人凭取货码签收。","2026-10-24 14:59:18")
    private val arrival=DetailPoint("待取件 快件已由学校校区菜鸟驿站代收，请及时取件。","2026-10-23 11:04:24")
    private val summary=PddDetailPayload.Snapshot(points=listOf(signed),waybill=item.mailNo)
    @Test fun statusPhaseMustReturnBeforeOptionalLocationPageCompletes()=runBlocking {
        val result=withTimeoutOrNull(100) {
            PddMetadataReader.read(item,emptyList(),{summary},{delay(1000);summary.copy(station="学校校区菜鸟驿站")})
        }
        assertNotNull("A ready status must not wait behind a slow optional location page",result)
        assertTrue(result!!.locationPending)
    }
    @Test fun validSignedSummaryMustNotPreventMissingLocationRepair()=runBlocking {
        var called=0
        val result=PddMetadataReader.read(item,emptyList(),{summary},{called++;summary.copy(station="学校校区菜鸟驿站",points=listOf(signed,arrival))},allowLocationLookup=true)!!
        assertEquals("Missing location must read the owned logistics page",1,called)
        assertEquals("学校校区菜鸟驿站",result.snapshot.station)
        assertEquals(ParcelPresentation.Stage.CLOSED,ParcelPresentation.stage(PddLogistics.apply(item,result.snapshot.points)))
    }
    @Test fun priorArrivalCanRestoreBlankLocationWithoutAnotherRequest()=runBlocking {
        val result=PddMetadataReader.read(item,listOf(arrival),{summary},{error("Cached arrival already has the location")})!!
        assertEquals("学校校区菜鸟驿站",result.snapshot.station)
        assertFalse(result.locationAttempted)
    }
    @Test fun knownLocationOrTransitKeepsTheFastSummaryPath()=runBlocking {
        for(current in listOf(item.copy(pickupStation="学校校区菜鸟驿站"),item.copy(logisticsStatus="运输中",state=0))) {
            val latest=if(current.state==0)summary.copy(points=listOf(DetailPoint("运输中 离开转运中心","2026-10-24 14:59:18"))) else summary
            val result=PddMetadataReader.read(current,emptyList(),{latest},{error("No location lookup needed")})!!
            assertTrue(result.summaryOnly)
        }
    }
    @Test fun optionalLocationFailureKeepsTheSuccessfulStatusUpdate()=runBlocking {
        val result=PddMetadataReader.read(item,emptyList(),{summary},{throw IllegalStateException("Official page unavailable")},allowLocationLookup=true)!!
        assertEquals(listOf(signed),result.snapshot.points)
        assertTrue(result.locationAttempted)
    }
    @Test fun optionalLocationBudgetDoesNotDiscardTheNewStatus()=runBlocking {
        val result=PddMetadataReader.read(item,emptyList(),{summary},{delay(1000);null},locationBudgetMs=10,allowLocationLookup=true)!!
        assertEquals(listOf(signed),result.snapshot.points)
        assertTrue(result.locationAttempted)
    }
    @Test fun mismatchedWaybillCannotSupplyAnotherParcelsLocation()=runBlocking {
        val result=PddMetadataReader.read(item,emptyList(),{summary},{summary.copy(waybill="OTHER0000012345",station="其他驿站")},allowLocationLookup=true)!!
        assertEquals("",result.snapshot.station)
        assertEquals(item.mailNo,result.snapshot.waybill)
    }
    @Test fun externalCancellationIsNotConvertedToOptionalLocationFailure()=runBlocking {
        try {
            PddMetadataReader.read(item,emptyList(),{summary},{throw CancellationException("stop")},allowLocationLookup=true)
            fail("Caller cancellation must propagate")
        } catch(_:CancellationException) { }
    }
    @Test fun batchReadsAllStatusesBeforeReusingSummariesForLocationEnrichment()=runBlocking {
        val oldStart=System.nanoTime()
        repeat(3){PddMetadataReader.read(item,emptyList(),{summary},{delay(80);summary.copy(station="学校校区菜鸟驿站")},allowLocationLookup=true)}
        val inlineMs=(System.nanoTime()-oldStart)/1_000_000
        val events=mutableListOf<String>()
        val pending=mutableListOf<PddMetadataReader.Result>()
        val fastStart=System.nanoTime()
        for(index in 1..3) {
            val status=PddMetadataReader.read(item,emptyList(),{events+="summary-$index";summary},{error("Status phase opened a location page")})!!
            events+="save-status-$index";if(status.locationPending)pending.add(status)
        }
        val statusMs=(System.nanoTime()-fastStart)/1_000_000
        var summaryReads=0
        for((index,ready) in pending.withIndex()) {
            val full=PddMetadataReader.read(item,emptyList(),{ready.snapshot},{events+="location-${index+1}";summary.copy(station="学校校区菜鸟驿站")},allowLocationLookup=true)!!
            assertEquals("学校校区菜鸟驿站",full.snapshot.station)
            summaryReads++
        }
        assertEquals(3,summaryReads)
        assertTrue(events.indexOf("save-status-3")<events.indexOf("location-1"))
        assertEquals(3,events.count {it.startsWith("summary-")})
        println("CONTROLLED_REPLAY parcels=3 optionalPageDelayMs=80 inlineStatusMs=$inlineMs deferredStatusMs=$statusMs")
    }
}
