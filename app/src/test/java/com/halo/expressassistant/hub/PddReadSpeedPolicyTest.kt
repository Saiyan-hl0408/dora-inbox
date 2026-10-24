package com.halo.expressassistant.hub

import com.halo.expressassistant.data.DetailPoint
import org.junit.Assert.*
import org.junit.Test

class PddReadSpeedPolicyTest {
    @Test fun pageReuseIsLimitedToSameOperationAndExactSession() {
        val pool=PddIdlePage<Any>();val owner=Any();val page=Any();val discarded=mutableListOf<Any>()
        pool.put(owner,"session-A",page,1000,discarded::add)
        assertSame(page,pool.take(owner,"session-A",1100,{true},discarded::add))
        assertNull(pool.take(owner,"session-A",1101,{true},discarded::add))
        pool.put(owner,"session-A",page,1200,discarded::add)
        assertNull(pool.take(owner,"session-B",1300,{true},discarded::add));assertEquals(listOf(page),discarded)
        pool.put(owner,"session-A",page,1400,discarded::add)
        assertNull(pool.take(Any(),"session-A",1500,{true},discarded::add));assertEquals(2,discarded.size)
    }
    @Test fun cancelledOrExpiredIdlePagesCannotBeReused() {
        val pool=PddIdlePage<Any>();val owner=Any();val page=Any();var drops=0
        pool.put(owner,"A",page,0){drops++}
        assertNull(pool.take(owner,"A",1,{false}){drops++})
        pool.put(owner,"A",page,0){drops++};pool.expire(4999){drops++};assertEquals(1,drops)
        pool.expire(5000){drops++};assertEquals(2,drops)
    }
    @Test fun olderExpiryTimerCannotDisposeARecentlyReturnedPage() {
        val pool=PddIdlePage<Any>();val owner=Any();val page=Any();var drops=0
        pool.put(owner,"A",page,0){drops++};assertSame(page,pool.take(owner,"A",100,{true}){drops++})
        pool.put(owner,"A",page,1000){drops++};pool.expire(5100){drops++}
        assertSame(page,pool.take(owner,"A",5200,{true}){drops++});assertEquals(0,drops)
    }
    @Test fun earlyShippingSummaryCannotFinishBeforeLogisticsLinkAppears() {
        val snapshot=PddDetailPayload.Snapshot(listOf(DetailPoint("已签收","2026-10-24 14:59")),orderStatus="待确认收货")
        assertFalse(PddLogisticsReadiness.terminalWithoutTrace(snapshot.copy(points=emptyList())))
        assertFalse(PddLogisticsReadiness.canFinish(snapshot,false,false,false))
        assertTrue(PddLogisticsReadiness.canFinish(snapshot,true,false,true))
        assertTrue(PddLogisticsReadiness.canFinish(snapshot,false,true,false))
        assertFalse(PddLogisticsReadiness.canFinish(snapshot.copy(nextUrl="/goods_express.html"),false,true,false))
    }
    @Test fun explicitlyUnshippedOrdersNeedNoLogisticsPage() {
        assertTrue(PddLogisticsReadiness.terminalWithoutTrace(PddDetailPayload.Snapshot(orderStatus="待发货")))
        assertFalse(PddLogisticsReadiness.terminalWithoutTrace(PddDetailPayload.Snapshot(orderStatus="待收货")))
    }
    @Test fun compactReportKeepsErrorsAndMissingDateWithoutDumpingResponseSamples() {
        val raw="""{"environment":{"appVersion":"0.9.8","androidApi":35},"login":[{"stage":"账号已保存"}],"sync":[{"stage":"分类与边界检查","facts":{"activeComplete":"true"}},{"stage":"订单响应识别","facts":{"event":"timeout","http":"0","recognized":"false"}}],"itemSnapshots":{"时间范围筛选后":[{"orderRef":"ANON","createdAt":0,"orderStatus":"待收货","stage":"TRANSIT"}]},"responses":[{"schema":"LARGE-FIELD-SAMPLE"}]}"""
        val report=PddDiagnosticSummary.render(raw)
        for(value in listOf("账号已保存","activeComplete=true","timeout","ANON","精简诊断结束"))assertTrue(value,report.contains(value))
        assertFalse(report.contains("LARGE-FIELD-SAMPLE"));assertTrue(report.length<1000)
    }
}
