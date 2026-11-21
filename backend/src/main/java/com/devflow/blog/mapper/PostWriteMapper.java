package com.devflow.blog.mapper;
import com.devflow.blog.domain.ManagedPost;
import com.devflow.blog.dto.PostWrite;
import java.util.List;
import org.apache.ibatis.annotations.*;
@Mapper
public interface PostWriteMapper {
 String SELECT="SELECT p.id,p.category_id,p.title,p.content_markdown,p.summary,p.status,p.author_id,a.username AS author_name,a.role AS author_role,p.created_at,p.updated_at,p.published_at,p.version,CAST(p.ai_draft_id AS text) AS ai_draft_id FROM blog.posts p LEFT JOIN blog.accounts a ON a.id=p.author_id ";
 @Select(SELECT+"WHERE p.deleted_at IS NULL ORDER BY p.updated_at DESC,p.id DESC LIMIT 500") List<ManagedPost> list();
 @Select(SELECT+"WHERE p.deleted_at IS NULL AND p.status IN ('DRAFT','IN_REVIEW') ORDER BY p.updated_at DESC,p.id DESC LIMIT 500") List<ManagedPost> drafts();
 @Select(SELECT+"WHERE p.id=#{id} AND p.deleted_at IS NULL") ManagedPost find(long id);
 @Select(SELECT+"WHERE p.id=#{id} AND p.deleted_at IS NULL FOR UPDATE OF p") ManagedPost lock(long id);
 @Select("INSERT INTO blog.posts(slug,title,content_markdown,summary,category_id,status,author_id,ai_draft_id) VALUES(#{slug},#{p.title},#{p.contentMarkdown},#{p.summary},#{p.categoryId},#{status},#{author},CAST(#{p.aiDraftId} AS uuid)) RETURNING id")
 long create(@Param("slug") String slug,@Param("p") PostWrite post,@Param("status") String status,@Param("author") long author);
 @Update("UPDATE blog.posts SET title=#{p.title},content_markdown=#{p.contentMarkdown},summary=COALESCE(#{p.summary},''),category_id=#{p.categoryId},status=#{status},ai_draft_id=COALESCE(CAST(#{p.aiDraftId} AS uuid),ai_draft_id),updated_at=now(),version=version+1 WHERE id=#{id}")
 void update(@Param("id") long id,@Param("p") PostWrite post,@Param("status") String status);
 @Update("UPDATE blog.posts SET status='PUBLISHED',published_at=now(),updated_at=now(),version=version+1 WHERE id=#{id}") void publish(long id);
 @Update("UPDATE blog.posts SET status='DRAFT',published_at=NULL,updated_at=now(),version=version+1 WHERE id=#{id}") void unpublish(long id);
 @Update("UPDATE blog.posts SET deleted_at=now(),updated_at=now(),version=version+1 WHERE id=#{id}") void delete(long id);
 @Select("SELECT count(*) FROM blog.categories WHERE id=#{id}") int category(long id);
}
