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
    @Select("""
        SELECT EXISTS(SELECT 1 FROM blog.posts WHERE ai_draft_id=CAST(#{id} AS uuid)
            AND deleted_at IS NULL AND (author_id=#{actorId} OR (visibility='PUBLIC' AND status='PUBLISHED' AND published_at<=CURRENT_TIMESTAMP)))
        """)
    boolean readableReference(@Param("id") String id,@Param("actorId") long actorId);
}
