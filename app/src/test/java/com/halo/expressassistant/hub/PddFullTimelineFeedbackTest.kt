package com.halo.expressassistant.hub

import org.junit.Assert.*
import org.junit.Test

class PddFullTimelineFeedbackTest {
    @Test fun officialShippingStoreContainsUnfoldedNodesAndFullPostalName() {
        val raw="""{"store":{"traceData":{"shipping":{"shippingName":"邮政电商标快","trackingNumber":"TEST8238896563","traces":[{"status":"SIGN","time":"2026-10-24 14:59:00","info":"您的快件已取走"},{"status":"UNKNOWN","time":"2026-10-23 12:00:00","info":"运输中 已离开转运中心"},{"time":"2026-10-22 10:00:00","info":"已揽收 快递员揽件"}]}}},"text":"已签收 2026-10-24 14:59:00\n您的快件已取走\n展开"}"""
        val snapshot=PddDetailPayload.read(raw)
        assertEquals("邮政电商标快",snapshot.company)
        assertEquals("TEST8238896563",snapshot.waybill)
        assertEquals(3,snapshot.points.size)
        assertEquals(ParcelPresentation.Stage.CLOSED,PddLogistics.stage(PddLogistics.status(snapshot.points.first().context)))
    }
    @Test fun listShippingNameIsNotLostOrShortenedWhenFullNameExists() {
        val metadata=TrackingFields.read("""{"shipping":{"shippingName":"邮政电商标快","trackingNumber":"TEST8238896563"},"logistics_company_short_name":"邮政"}""")
        assertEquals("邮政电商标快",metadata.company)
        assertEquals("TEST8238896563",metadata.waybill)
    }
    @Test fun postalNameInTheOfficialHeaderDoesNotDependOnACourierAllowlist() {
        val snapshot=PddDetailPayload.read("""{"text":"邮政电商标快：TEST8238896563\n已签收 2026-10-24 14:59:00\n快件已领取"}""")
        assertEquals("邮政电商标快",snapshot.company)
        assertEquals("TEST8238896563",snapshot.waybill)
    }
}
