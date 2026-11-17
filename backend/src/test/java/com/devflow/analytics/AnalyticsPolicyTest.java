package com.devflow.analytics;
import com.devflow.analytics.service.*;
import com.devflow.analytics.dto.AnalyticsDtos.PageView;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AnalyticsPolicyTest {
 @Test void rangesAreInclusiveAndCompareEqualDurations(){
  var r=AnalyticsRange.of(null,LocalDate.of(2026,1,1),LocalDate.of(2026,1,7));
  assertEquals(7*86400,r.until().toEpochSecond()-r.from().toEpochSecond());
  assertEquals(LocalDate.of(2025,12,25),r.previous().start());
  assertEquals("+09:00",r.from().getOffset().toString());
  assertThrows(RuntimeException.class,()->AnalyticsRange.of(8,null,null));
  assertThrows(RuntimeException.class,()->AnalyticsRange.of(null,LocalDate.now().minusDays(400),LocalDate.now()));
  assertNull(AnalyticsService.change(1,0));assertEquals(-50.0,AnalyticsService.change(5,10));
 }
 @Test void attributionDoesNotTrustLookalikeDomainsOrReattributeInternalNavigation(){
  assertEquals("GOOGLE",VisitService.attribution("https://www.google.com/search?q=private",null,"https://blog.test").source());
  assertEquals("OTHER",VisitService.attribution("https://google.com.evil.test",null,"https://blog.test").source());
  assertEquals("INTERNAL",VisitService.attribution("https://blog.test/posts/1","google","https://blog.test").source());
  assertEquals("DIRECT",VisitService.attribution(null,null,"https://blog.test").source());
  assertNull(VisitService.domain("javascript:alert(1)"));
 }
 @Test void onlyCanonicalPublicPathsAndMatchingPostIdsAreAccepted(){
  VisitService.validate(new PageView(UUID.randomUUID(),"/posts?categoryId=1&page=2",null,null,null,null,null,true));
  assertThrows(RuntimeException.class,()->VisitService.validate(new PageView(UUID.randomUUID(),"/admin",null,null,null,null,null,true)));
  assertThrows(RuntimeException.class,()->VisitService.validate(new PageView(UUID.randomUUID(),"/posts/1",2L,null,null,null,null,true)));
  assertThrows(RuntimeException.class,()->VisitService.validate(new PageView(UUID.randomUUID(),"/",null,null,"person@email.test",null,null,true)));
 }
 @Test void rateLimitIsBoundedAndSeparatesAddresses(){
  var limiter=new VisitRateLimiter();for(int i=0;i<60;i++)assertTrue(limiter.allow("test-address"));
  assertFalse(limiter.allow("test-address"));assertTrue(limiter.allow("other-address"));
 }
}
