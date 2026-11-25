package com.devflow.auth;

import com.devflow.auth.mapper.AccountMapper;
import com.devflow.auth.service.AccountService;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres") @SpringBootTest
class TokenLifecyclePostgresTest {
    @Autowired AccountService service; @Autowired AccountMapper mapper; @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Test void revocationWaitsForConcurrentIssuanceAndLeavesNoUsableToken() throws Exception {
        String name="lifecycle-"+UUID.randomUUID();
        long id=mapper.create(name,name+"@example.test",null,"SYSTEM");
        var pending=new AtomicReference<Future<?>>(); var started=new CountDownLatch(1);
        try(var executor=Executors.newSingleThreadExecutor()) {
            var tx=new TransactionTemplate(manager);
            String issued=tx.execute(status->{
                String token=service.issue(id);
                pending.set(executor.submit(()->{started.countDown();service.revoke(id);}));
                try {
                    assertTrue(started.await(5,TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class,()->pending.get().get(200,TimeUnit.MILLISECONDS));
                } catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                return token;
            });
            pending.get().get(10,TimeUnit.SECONDS);
            assertNull(service.token(issued));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM blog.service_tokens WHERE account_id=? AND revoked_at IS NULL",Integer.class,id));
        } finally {
            // Remove only the synthetic account and tokens owned by this test.
            jdbc.update("DELETE FROM blog.service_tokens WHERE account_id=?",id);
            jdbc.update("DELETE FROM blog.accounts WHERE id=? AND username=?",id,name);
        }
    }
}
