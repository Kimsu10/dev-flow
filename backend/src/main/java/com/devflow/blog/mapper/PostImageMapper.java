package com.devflow.blog.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface PostImageMapper {
    record Image(String id, long postId, String objectKey, String contentType, int byteSize) {}
    @Insert("INSERT INTO blog.post_images(id,post_id,object_key,content_type,byte_size) VALUES(CAST(#{id} AS uuid),#{postId},#{objectKey},#{contentType},#{byteSize})")
    void insert(Image image);
    @Select("SELECT CAST(id AS text) AS id,post_id,object_key,content_type,byte_size FROM blog.post_images WHERE id=CAST(#{id} AS uuid) AND post_id=#{postId}")
    Image find(@Param("postId") long postId,@Param("id") String id);
    @Select("SELECT name FROM blog.categories WHERE id=#{id}") String category(long id);
    @Update("UPDATE blog.posts SET content_markdown=#{markdown} WHERE id=#{id}")
    void content(@Param("id") long id,@Param("markdown") String markdown);
}
