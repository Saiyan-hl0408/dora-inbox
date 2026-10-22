package com.halo.expressassistant.hub

import com.halo.expressassistant.data.ExpressItem
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder

class IdentityRoutesTest {
    @Test fun jdIdentityWebNavigationRejectsUnrelatedOrInsecureDestinations() {
        assertTrue(IdentityWebPolicy.allowed(IdentityRoutes.JD_URL, "jd"))
        assertTrue(IdentityWebPolicy.allowed("https://passport.jd.com/new/login.aspx", "jd"))
        assertTrue(IdentityWebPolicy.loginPage("https://passport.jd.com/new/login.aspx", "jd"))
        assertFalse(IdentityWebPolicy.allowed("http://convenient-h5.jd.com/identityCode", "jd"))
        assertFalse(IdentityWebPolicy.allowed("https://jd.com.example.com/identity", "jd"))
        assertFalse(IdentityWebPolicy.allowed(IdentityRoutes.TB_URL, "jd"))
        assertFalse(IdentityWebPolicy.allowed(IdentityRoutes.PDD_URL, "pdd"))
    }
    @Test fun embeddedIdentityOnlyNavigatesToOfficialSecurePages() {
        assertTrue(IdentityWebPolicy.allowed(IdentityRoutes.TB_URL))
        assertTrue(IdentityWebPolicy.loginPage("https://login.m.taobao.com/login.htm"))
        for(url in listOf("http://pages-fast.m.taobao.com/identity","https://taobao.com.example.com/identity","taobao://open","https://example.com/?from=taobao.com"))
            assertFalse(IdentityWebPolicy.allowed(url))
        assertFalse(IdentityWebPolicy.loginPage("https://evil.example/login"))
    }
    @Test fun jdCardTargetsActualIdentityPageInsteadOfOrdersOrCampusLanding() {
        val route=IdentityRoutes.route("jd")
        assertEquals("https://convenient-h5.jd.com/identityCode?channel=xueshengjirukou",route.webUrl)
        assertTrue(URLDecoder.decode(route.appUris.first(),"UTF-8").contains(route.webUrl))
        assertFalse(route.appUris.any {it.contains("orderlist")})
    }
    @Test fun oneParcelDeterminesOneDestinationWithNoPlatformListSplit() {
        val item=ExpressItem("a","","极兔","TEST123456",source="pdd")
        assertEquals("",IdentityRoutes.forParcel(item))
        assertEquals("jd",IdentityRoutes.forParcel(item.copy(companyName="京东快递")))
        assertEquals("",IdentityRoutes.forParcel(item.copy(source="taobao")))
        assertEquals("taobao",IdentityRoutes.forParcel(item.copy(identityChannel="cainiao")))
    }
    @Test fun pddIdentityRouteIncludesTheIdentityTab() {
        assertTrue(IdentityRoutes.route("pdd").appUris.first().contains("tab=ID_CODE"))
    }
}
