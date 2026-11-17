package com.devflow.blog.mapper;

import com.devflow.blog.domain.CategoryRow;
import com.devflow.blog.domain.PostRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BlogMapper {
    String POST_COLUMNS = """
        p.id, p.title, p.summary, p.thumbnail_url, p.created_at, p.published_at,
        CAST(NULL AS text) AS content_markdown,
        c.id AS category_id, c.name AS category_name, c.slug AS category_slug, c.sort_order AS category_sort_order
        """;
    String VISIBLE = "p.status = 'PUBLISHED' AND p.deleted_at IS NULL AND p.published_at <= CURRENT_TIMESTAMP";
    String VISIBLE_XML = "p.status = 'PUBLISHED' AND p.deleted_at IS NULL AND p.published_at &lt;= CURRENT_TIMESTAMP";

    @Select("""
        WITH ranked_posts AS (
            SELECT id, category_id, title, summary, thumbnail_url, created_at, published_at,
                   row_number() OVER (PARTITION BY category_id ORDER BY published_at DESC, id DESC) AS position
            FROM blog.posts p WHERE """ + " " + VISIBLE + """
        )
        SELECT """ + " " + POST_COLUMNS + """
        FROM ranked_posts p JOIN blog.categories c ON c.id = p.category_id
        WHERE p.position <= 3
        ORDER BY c.sort_order ASC, c.id ASC, p.published_at DESC, p.id DESC
        """)
    List<PostRow> findHomePosts();

    @Select("SELECT id, name, slug, sort_order FROM blog.categories ORDER BY sort_order, id")
    List<CategoryRow> findCategories();

    @Select("<script>SELECT " + POST_COLUMNS + " FROM blog.posts p LEFT JOIN blog.categories c ON c.id=p.category_id WHERE "
        + VISIBLE_XML + " <if test='categoryId != null'>AND p.category_id=#{categoryId}</if>"
        + " ORDER BY p.published_at DESC, p.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<PostRow> findPosts(@Param("categoryId") Long categoryId, @Param("size") int size, @Param("offset") long offset);

    @Select("<script>SELECT count(*) FROM blog.posts p WHERE " + VISIBLE_XML
        + " <if test='categoryId != null'>AND p.category_id=#{categoryId}</if></script>")
    long countPosts(@Param("categoryId") Long categoryId);

    @Select("""
        SELECT p.id, p.title, p.summary, p.thumbnail_url, p.created_at, p.published_at, p.content_markdown,
               c.id AS category_id, c.name AS category_name, c.slug AS category_slug, c.sort_order AS category_sort_order
        FROM blog.posts p LEFT JOIN blog.categories c ON c.id = p.category_id
        WHERE p.id = #{id} AND """ + " " + VISIBLE)
    PostRow findPost(@Param("id") long id);
}
