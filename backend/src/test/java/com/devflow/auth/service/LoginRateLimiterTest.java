package com.devflow.auth.service;

import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTest {
    private static class TestClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now.get(); }
        void advance(long seconds) { now.updateAndGet(i -> i.plusSeconds(seconds)); }
    }
    @Test void identityBudgetNormalizesCaseAndRecoversWithoutExtendingTheWindow() {
        var clock = new TestClock(); var limiter = new LoginRateLimiter(clock);
        for (int i=0;i<10;i++) assertTrue(limiter.identity("Synthetic-Identity").allowed());
        assertFalse(limiter.identity("synthetic-identity").allowed());
        assertEquals(300,limiter.identity("SYNTHETIC-IDENTITY").retryAfterSeconds());
        clock.advance(299); assertEquals(1,limiter.identity("synthetic-identity").retryAfterSeconds());
        clock.advance(1); assertTrue(limiter.identity("synthetic-identity").allowed());
    }
    @Test void addressAndIdentityBudgetsAreIndependentAndAddressExpires() {
        var clock = new TestClock(); var limiter = new LoginRateLimiter(clock);
        for (int i=0;i<30;i++) assertTrue(limiter.address("synthetic-peer").allowed());
        assertFalse(limiter.address("synthetic-peer").allowed());
        assertTrue(limiter.address("other-peer").allowed());
        assertTrue(limiter.identity("synthetic-peer").allowed());
        clock.advance(60); assertTrue(limiter.address("synthetic-peer").allowed());
    }
    @Test void saturatedStorageDoesNotEvictActiveBudgetsAndReclaimsExpiredEntries() {
        var clock = new TestClock(); var limiter = new LoginRateLimiter(clock);
        for(int i=0;i<10_000;i++) assertTrue(limiter.identity("fixture-"+i).allowed());
        assertFalse(limiter.identity("new-fixture").allowed());
        for(int i=1;i<10;i++) assertTrue(limiter.identity("fixture-0").allowed());
        assertFalse(limiter.identity("fixture-0").allowed());
        clock.advance(300); assertTrue(limiter.identity("new-fixture").allowed());
    }
    @Test void concurrentRequestsReserveTheBudgetAtomically() throws Exception {
        var limiter = new LoginRateLimiter(new TestClock());
        try(var executor=Executors.newFixedThreadPool(8)) {
            var tasks=new java.util.ArrayList<Future<Boolean>>();
            for(int i=0;i<50;i++) tasks.add(executor.submit(()->limiter.identity("same-fixture").allowed()));
            int accepted=0; for(var task:tasks) if(task.get(5,TimeUnit.SECONDS))accepted++;
            assertEquals(10,accepted);
        }
    }
}
