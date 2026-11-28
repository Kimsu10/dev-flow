package com.devflow.blog.mapper;
import com.devflow.blog.domain.CategoryRow;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TrialAiMapper {
    @Select("SELECT id,name,slug,sort_order FROM blog.categories WHERE blog.category_name_key(name)=blog.category_name_key('체험용')")
    CategoryRow category();
    @Select("SELECT COALESCE((SELECT requests FROM blog.trial_ai_usage WHERE account_id=#{accountId} AND usage_date=(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date),0)")
    int used(long accountId);
    @Select(value="""
        INSERT INTO blog.trial_ai_usage(account_id,usage_date,requests)
        VALUES(#{accountId},(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date,1)
        ON CONFLICT(account_id,usage_date) DO UPDATE SET requests=blog.trial_ai_usage.requests+1
        WHERE blog.trial_ai_usage.requests < #{limit}
        RETURNING requests
        """,affectData=true)
    @Options(flushCache=Options.FlushCachePolicy.TRUE,useCache=false)
    Integer consume(@Param("accountId") long accountId,@Param("limit") int limit);
}
