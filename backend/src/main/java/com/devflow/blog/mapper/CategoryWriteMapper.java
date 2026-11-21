package com.devflow.blog.mapper;

import com.devflow.blog.domain.CategoryRow;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CategoryWriteMapper {
    @Select(value = """
        INSERT INTO blog.categories(name,slug) VALUES(#{name},#{slug})
        ON CONFLICT (blog.category_name_key(name)) DO UPDATE SET name=blog.categories.name
        RETURNING id,name,slug,sort_order
        """, affectData = true)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    CategoryRow createOrFind(@Param("name") String name, @Param("slug") String slug);
}
