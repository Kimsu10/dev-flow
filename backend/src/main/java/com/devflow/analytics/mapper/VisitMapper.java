package com.devflow.analytics.mapper;
import com.devflow.analytics.dto.AnalyticsDtos.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.apache.ibatis.annotations.*;
@Mapper
public interface VisitMapper {
 @Select("SELECT id,category_id FROM blog.posts p WHERE p.id=#{id} AND "+AnalyticsMapper.VISIBLE) PostTarget post(long id);
 @Select("SELECT id,visitor_hash,started_at,last_seen_at FROM blog.visitor_sessions WHERE id=#{id} AND visitor_hash=#{visitor} FOR UPDATE")
 VisitSession session(@Param("id") UUID id,@Param("visitor") String visitor);
 @Insert("INSERT INTO blog.visitor_sessions(id,visitor_hash,started_at,last_seen_at,source,referrer_domain,utm_source,utm_medium,utm_campaign) VALUES(#{id},#{visitor},#{now},#{now},#{source},#{domain},#{p.utmSource},#{p.utmMedium},#{p.utmCampaign})")
 void create(@Param("id") UUID id,@Param("visitor") String visitor,@Param("now") OffsetDateTime now,@Param("source") String source,@Param("domain") String domain,@Param("p") PageView p);
 @Update("UPDATE blog.visitor_sessions SET last_seen_at=#{now} WHERE id=#{id}") void touch(@Param("id") UUID id,@Param("now") OffsetDateTime now);
 @Insert("INSERT INTO blog.page_view_events(id,session_id,visitor_hash,post_id,category_id,path,occurred_at,dedup_bucket) VALUES(#{p.eventId},#{session},#{visitor},#{p.postId},#{category},#{p.path},#{now},#{bucket}) ON CONFLICT DO NOTHING")
 int event(@Param("p") PageView p,@Param("session") UUID session,@Param("visitor") String visitor,@Param("category") Long category,@Param("now") OffsetDateTime now,@Param("bucket") long bucket);
 @Delete("DELETE FROM blog.visitor_sessions WHERE id=#{id} AND NOT EXISTS (SELECT 1 FROM blog.page_view_events WHERE session_id=#{id})") void removeEmpty(@Param("id") UUID id);
 @Select("SELECT EXISTS(SELECT 1 FROM blog.page_view_events WHERE id=#{id})") boolean exists(@Param("id") UUID id);
 @Delete("DELETE FROM blog.page_view_events WHERE occurred_at < #{cutoff}") int purgeEvents(OffsetDateTime cutoff);
 @Delete("DELETE FROM blog.visitor_sessions WHERE last_seen_at < #{cutoff}") int purgeSessions(OffsetDateTime cutoff);
}
