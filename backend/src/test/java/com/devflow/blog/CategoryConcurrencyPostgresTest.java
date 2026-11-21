package com.devflow.blog;

import com.devflow.blog.service.CategoryWriteService;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres") @SpringBootTest
class CategoryConcurrencyPostgresTest {
    @Autowired CategoryWriteService service; @Autowired JdbcTemplate jdbc;
    @Test void concurrentTransactionsCreateExactlyOneCategory() throws Exception {
        String name="concurrent-"+UUID.randomUUID();
        var start=new CountDownLatch(1);
        try (var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(()->{start.await();return service.create(name).id();});
            var second=executor.submit(()->{start.await();return service.create(name.toUpperCase(Locale.ROOT)).id();});
            start.countDown();
            assertEquals(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM blog.categories WHERE blog.category_name_key(name)=blog.category_name_key(?)",Integer.class,name));
        } finally {
            // Only the unique fixture owned by this test is removed; no existing data is touched.
            jdbc.update("DELETE FROM blog.categories WHERE blog.category_name_key(name)=blog.category_name_key(?)",name);
        }
    }
}
