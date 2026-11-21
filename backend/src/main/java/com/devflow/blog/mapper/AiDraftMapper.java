package com.devflow.blog.mapper;

import com.devflow.blog.domain.AiDraftRow;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AiDraftMapper {
    @Insert("""
        INSERT INTO blog.ai_drafts(id,author_id,question,content_markdown,sources_json)
        VALUES(CAST(#{id} AS uuid),#{authorId},#{question},#{contentMarkdown},#{sourcesJson})
        """)
    void create(AiDraftRow row);
    @Select("SELECT CAST(id AS text) AS id,author_id,question,content_markdown,sources_json FROM blog.ai_drafts WHERE id=CAST(#{id} AS uuid)")
    AiDraftRow find(String id);
    @Select("""
        SELECT CAST(d.id AS text) AS id,d.author_id,d.question,d.content_markdown,d.sources_json
        FROM blog.ai_drafts d JOIN blog.posts p ON p.ai_draft_id=d.id WHERE p.id=#{postId}
        """)
    AiDraftRow forPost(long postId);
}
