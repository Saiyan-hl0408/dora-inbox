package com.halo.expressassistant.hub

import org.junit.Assert.*
import org.junit.Test

class TrackingFieldsTest {
    @Test fun missingLogisticsNeverBecomesAnOrderNumberOrInventedCarrier() {
        val value=TrackingFields.read("""{"order_sn":"ORDER123456789","order_status_prompt":"已完成"}""")
        assertEquals("",value.waybill)
        assertEquals("",value.company)
    }
    @Test fun nestedShippingFieldsProvideActualWaybillAndCarrier() {
        val value=TrackingFields.read("""{"order_sn":"ORDER123456789","shipping_info":{"tracking_number":"JT123456789012","logistics_company_name":"极兔速递"}}""")
        assertEquals("JT123456789012",value.waybill)
        assertEquals("极兔速递",value.company)
    }
}
