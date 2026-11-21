package com.devflow.blog.mapper;
import com.devflow.blog.domain.ManagedPost;
import com.devflow.blog.dto.PostWrite;
import java.util.List;
import org.apache.ibatis.annotations.*;
@Mapper
public interface PostWriteMapper {
 String SELECT="SELECT p.id,p.category_id,p.title,p.content_markdown,p.summary,p.status,p.author_id,a.username AS author_name,a.role AS author_role,p.created_at,p.updated_at,p.published_at,p.version,CAST(p.ai_draft_id AS text) AS ai_draft_id,p.visibility FROM blog.posts p LEFT JOIN blog.accounts a ON a.id=p.author_id ";
 String READABLE="p.deleted_at IS NULL AND (p.visibility='PUBLIC' OR p.author_id=#{actorId})";
 String ORDER=" ORDER BY p.published_at DESC NULLS LAST,p.id DESC LIMIT 500";
 @Select(SELECT+"WHERE "+READABLE+ORDER) List<ManagedPost> list(long actorId);
 @Select(SELECT+"WHERE "+READABLE+" AND p.status IN ('DRAFT','IN_REVIEW')"+ORDER) List<ManagedPost> drafts(long actorId);
 @Select(SELECT+"WHERE p.deleted_at IS NULL AND p.author_id=#{actorId}"+ORDER) List<ManagedPost> mine(long actorId);
 @Select(SELECT+"WHERE p.id=#{id} AND p.deleted_at IS NULL") ManagedPost find(long id);
 @Select(SELECT+"WHERE p.id=#{id} AND p.deleted_at IS NULL FOR UPDATE OF p") ManagedPost lock(long id);
 @Select("INSERT INTO blog.posts(slug,title,content_markdown,summary,category_id,status,author_id,ai_draft_id,published_at,visibility) VALUES(#{slug},#{p.title},#{p.contentMarkdown},#{p.summary},#{p.categoryId},#{status},#{author},CAST(#{p.aiDraftId} AS uuid),COALESCE(#{p.publishedAt},CURRENT_TIMESTAMP),#{p.visibility}) RETURNING id")
 long create(@Param("slug") String slug,@Param("p") PostWrite post,@Param("status") String status,@Param("author") long author);
 @Update("UPDATE blog.posts SET title=#{p.title},content_markdown=#{p.contentMarkdown},summary=COALESCE(#{p.summary},''),category_id=#{p.categoryId},status=#{status},ai_draft_id=COALESCE(CAST(#{p.aiDraftId} AS uuid),ai_draft_id),published_at=COALESCE(#{p.publishedAt},CASE WHEN #{status}='PUBLISHED' THEN CURRENT_TIMESTAMP ELSE NULL END),visibility=#{p.visibility},author_id=#{authorId},updated_at=now(),version=version+1 WHERE id=#{id}")
 void update(@Param("id") long id,@Param("p") PostWrite post,@Param("status") String status,@Param("authorId") Long authorId);
 @Update("UPDATE blog.posts SET status='PUBLISHED',visibility='PUBLIC',published_at=COALESCE(published_at,now()),updated_at=now(),version=version+1 WHERE id=#{id}") void publish(long id);
 @Update("UPDATE blog.posts SET status='DRAFT',updated_at=now(),version=version+1 WHERE id=#{id}") void unpublish(long id);
 @Update("UPDATE blog.posts SET deleted_at=now(),updated_at=now(),version=version+1 WHERE id=#{id}") void delete(long id);
 @Select("SELECT count(*) FROM blog.categories WHERE id=#{id}") int category(long id);
}
