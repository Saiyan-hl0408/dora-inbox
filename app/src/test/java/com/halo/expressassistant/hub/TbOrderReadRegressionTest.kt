package com.halo.expressassistant.hub

import com.halo.expressassistant.api.TbOrderRead
import com.halo.expressassistant.api.TbOrderResponse
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class TbOrderReadRegressionTest {
    @Test fun fallbackKeepsBothFailureStagesWithoutPrivateMessages()=runBlocking {
        val failure=runCatching {
            TbOrderRead.page({throw IllegalStateException("private cookie and order text")},
                {throw TbOrderResponse.Failure("SDK_PREPARE_TIMEOUT")})
        }.exceptionOrNull()!!
        assertTrue(failure.message.orEmpty().contains("SDK_PREPARE_TIMEOUT"))
        assertTrue(failure.message.orEmpty().contains("IllegalStateException"))
        assertFalse(failure.message.orEmpty().contains("private"))
        assertFalse(TbOrderRead.needsLogin(failure))
    }
    @Test fun onlyExplicitLatestAuthFailureInvalidatesOrderVerification()=runBlocking {
        assertTrue(TbOrderRead.needsLogin(TbOrderResponse.Failure("FAIL_SYS_SESSION_EXPIRED")))
        assertTrue(TbOrderRead.needsLogin(TbOrderResponse.Failure("FAIL_SYS_AUTH_REJECT")))
        for(code in listOf("ORDER_READ_TIMEOUT","ORDER_REQUEST_TIMEOUT","SDK_PREPARE_TIMEOUT","UNSUPPORTED_ORDER_DATA","FAIL_SYS_TOKEN_EXOIRED"))
            assertFalse(TbOrderRead.needsLogin(TbOrderResponse.Failure(code)))
        val staleDirect=runCatching {TbOrderRead.page({throw TbOrderResponse.Failure("FAIL_SYS_SESSION_EXPIRED")},
            {throw TbOrderResponse.Failure("ORDER_REQUEST_TIMEOUT")})}.exceptionOrNull()!!
        assertFalse(TbOrderRead.needsLogin(staleDirect))
    }
    @Test fun fallbackSuccessAndCancellationRemainDistinct()=runBlocking {
        val empty=TbOrderResponse.Page(emptyList(),false)
        assertEquals(empty,TbOrderRead.page({throw TbOrderResponse.Failure("INVALID_RESPONSE")},{empty}))
        var called=false
        val failure=runCatching {TbOrderRead.page({throw CancellationException("cancel")},{called=true;empty})}.exceptionOrNull()
        assertTrue(failure is CancellationException);assertFalse(called)
    }
}
