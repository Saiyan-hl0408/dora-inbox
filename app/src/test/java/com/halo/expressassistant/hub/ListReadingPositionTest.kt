package com.halo.expressassistant.hub

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ListReadingPositionTest {
    private val rows=listOf(ListReadingPosition.Row("A",100,250),ListReadingPosition.Row("B",350,250),ListReadingPosition.Row("C",600,250))
    @Test fun returnFromDetailsKeepsPartiallyVisibleParcelAndOffset() {
        val saved=ListReadingPosition.capture(420,rows)
        assertEquals("B",saved.rowKey);assertEquals(70,saved.offset)
        assertEquals(420,ListReadingPosition.restore(saved,rows,1000))
    }
    @Test fun syncInsertsAboveVisibleCardWithoutMovingReadingToAnotherParcel() {
        val saved=ListReadingPosition.capture(420,rows)
        val updated=listOf(ListReadingPosition.Row("new",100,120))+rows.map {it.copy(top=it.top+120)}
        assertEquals(540,ListReadingPosition.restore(saved,updated,1500))
    }
    @Test fun removedAnchorFallsBackToOldOffsetAndClampsToAvailableContent() {
        val saved=ListReadingPosition.capture(420,rows)
        assertEquals(420,ListReadingPosition.restore(saved,rows.filterNot {it.key=="B"},1000))
        assertEquals(200,ListReadingPosition.restore(saved,emptyList(),200))
    }
    @Test fun historyAndMainAndFiltersHaveIndependentBookmarksAcrossRecreation() {
        val main=ListReadingPosition.scope(false,180,"全部平台","")
        val history=ListReadingPosition.scope(true,180,"全部平台","")
        val filtered=ListReadingPosition.scope(true,180,"拼多多","")
        val saved=mapOf(main to ListReadingPosition.Bookmark(scrollY=40),history to ListReadingPosition.capture(420,rows))
        val restored=Json.decodeFromString<Map<String,ListReadingPosition.Bookmark>>(Json.encodeToString(saved))
        assertEquals(40,ListReadingPosition.restore(restored[main],emptyList(),1000))
        assertEquals(420,ListReadingPosition.restore(restored[history],rows,1000))
        assertEquals(0,ListReadingPosition.restore(restored[filtered],rows,1000))
    }
    @Test fun topPositionAndEmptyListAreStable() {
        assertEquals(0,ListReadingPosition.restore(ListReadingPosition.capture(0,rows),rows,1000))
        assertEquals(0,ListReadingPosition.restore(ListReadingPosition.capture(0,rows),rows.map {it.copy(top=it.top+120)},1000))
        assertEquals(0,ListReadingPosition.restore(null,rows,1000))
    }
}
