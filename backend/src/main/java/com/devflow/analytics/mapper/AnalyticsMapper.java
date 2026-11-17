package com.devflow.analytics.mapper;
import com.devflow.analytics.dto.AnalyticsDtos.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.apache.ibatis.annotations.*;
@Mapper
public interface AnalyticsMapper {
 String WINDOW="occurred_at >= #{from} AND occurred_at < #{until}";
 String VISIBLE="p.status='PUBLISHED' AND p.deleted_at IS NULL AND p.published_at <= CURRENT_TIMESTAMP";
 @Select("SELECT count(DISTINCT visitor_hash) AS visitors,count(*) AS page_views,count(DISTINCT session_id) AS visits FROM blog.page_view_events WHERE "+WINDOW)
 Counts counts(@Param("from") OffsetDateTime from,@Param("until") OffsetDateTime until);
 @Select("SELECT count(DISTINCT visitor_hash) AS visitors,count(*) AS page_views,count(DISTINCT session_id) AS visits FROM blog.page_view_events") Counts totals();
 @Select("SELECT count(*) FROM blog.posts p WHERE "+VISIBLE) long postCount();
 @Select("SELECT (occurred_at AT TIME ZONE 'Asia/Seoul')::date AS date,count(DISTINCT visitor_hash) AS visitors,count(*) AS page_views FROM blog.page_view_events WHERE "+WINDOW+" GROUP BY 1 ORDER BY 1")
 List<Traffic> traffic(@Param("from") OffsetDateTime from,@Param("until") OffsetDateTime until);
 @Select("SELECT source,count(*) AS visits,0.0 AS percentage FROM blog.visitor_sessions WHERE started_at >= #{from} AND started_at < #{until} GROUP BY source ORDER BY visits DESC,source")
 List<Referrer> referrers(@Param("from") OffsetDateTime from,@Param("until") OffsetDateTime until);
 String POST_STATS="WITH views AS (SELECT post_id,count(*) AS total_views,count(*) FILTER (WHERE "+WINDOW+") AS period_views FROM blog.page_view_events WHERE post_id IS NOT NULL GROUP BY post_id) SELECT p.id,p.title,p.created_at,p.updated_at,COALESCE(v.total_views,0) AS total_views,COALESCE(v.period_views,0) AS period_views FROM blog.posts p LEFT JOIN views v ON v.post_id=p.id WHERE "+VISIBLE;
 @Select(POST_STATS+" ORDER BY period_views DESC,total_views DESC,p.id DESC LIMIT 10")
 List<PostStats> popular(@Param("from") OffsetDateTime from,@Param("until") OffsetDateTime until);
 @Select(POST_STATS+" ORDER BY total_views DESC,p.id DESC LIMIT 10")
 List<PostStats> allTime(@Param("from") OffsetDateTime from,@Param("until") OffsetDateTime until);
 @Select(POST_STATS+" AND p.created_at >= #{from} AND p.created_at < #{until} ORDER BY p.created_at DESC,p.id DESC LIMIT 10")
 List<PostStats> recent(@Param("from") OffsetDateTime from,@Param("until") OffsetDateTime until);
 @Select("SELECT (occurred_at AT TIME ZONE 'Asia/Seoul')::date AS date,count(DISTINCT visitor_hash) AS visitors,count(*) AS page_views FROM blog.page_view_events WHERE "+WINDOW+" AND post_id=#{id} GROUP BY 1 ORDER BY 1")
 List<Traffic> postTraffic(@Param("id") long id,@Param("from") OffsetDateTime from,@Param("until") OffsetDateTime until);
 @Select("""
 WITH groups AS (SELECT id,name FROM blog.categories UNION ALL SELECT NULL::bigint,'미분류'),
 posts AS (SELECT category_id,count(*) AS count FROM blog.posts p WHERE
 """+VISIBLE+" "+"""
 GROUP BY category_id), views AS (
 SELECT category_id,count(*) AS total_views,count(*) FILTER (WHERE
 """+WINDOW+"""
 ) AS period_views FROM blog.page_view_events WHERE post_id IS NOT NULL GROUP BY category_id)
 SELECT g.id,g.name,COALESCE(p.count,0) AS post_count,COALESCE(v.total_views,0) AS total_views,
 COALESCE(v.period_views,0) AS period_views,0.0 AS percentage FROM groups g
 LEFT JOIN posts p ON p.category_id IS NOT DISTINCT FROM g.id
 LEFT JOIN views v ON v.category_id IS NOT DISTINCT FROM g.id ORDER BY period_views DESC,g.name
 """) List<CategoryStats> categories(@Param("from") OffsetDateTime from,@Param("until") OffsetDateTime until);
}
