package com.halo.expressassistant.hub

import org.junit.Assert.*
import org.junit.Test

class SyncCoverageProofsTest {
    @Test fun stagedBoundaryDoesNotPublishBeforeSuccessfulExplicitCommit() {
        val proofs=SyncCoverageProofs<Long>()
        val committed=mutableListOf<Long>()
        proofs.stage("jd-account",100L)
        assertTrue(committed.isEmpty())
        proofs.commit {committed.add(it);true}
        assertEquals(listOf(100L),committed)
        proofs.commit {committed.add(it);true}
        assertEquals(1,committed.size)
    }
    @Test fun cancelledReadCannotPublishItsBoundaryInTheNextSession() {
        val proofs=SyncCoverageProofs<Long>()
        val committed=mutableListOf<Long>()
        proofs.stage("pdd-account",90L)
        proofs.clear()
        proofs.stage("jd-account",30L)
        proofs.commit {committed.add(it);true}
        assertEquals(listOf(30L),committed)
    }
    @Test fun failedPersistenceCanRetryInsteadOfLosingItsBoundaryProof() {
        val proofs=SyncCoverageProofs<Long>()
        proofs.stage("taobao-account",30L)
        proofs.commit {false}
        val committed=mutableListOf<Long>()
        proofs.commit {committed.add(it);true}
        assertEquals(listOf(30L),committed)
    }
}
