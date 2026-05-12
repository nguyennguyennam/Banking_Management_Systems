package com.bvb.sharedmodule.config.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Caffeine-backed CacheManager with per-cache TTL and size limits.
 *
 * Cache inventory:
 *   account            – single account by ID; evicted on any mutation
 *   account-stats      – aggregation query; short TTL acceptable
 *   account-by-customer – account list per customer + page; evicted on create/close
 *   transaction        – single transaction by ID; short TTL (status can change)
 *   transaction-history – paginated history per account; short TTL (new txs arrive)
 */
@Configuration
@EnableCaching
public class CaffeineCacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                build("account",              500,  Duration.ofMinutes(10)),
                build("account-stats",          1,  Duration.ofMinutes(5)),
                build("account-by-customer",  200,  Duration.ofMinutes(5)),
                build("transaction",         1000,  Duration.ofMinutes(2)),
                build("transaction-history",  200,  Duration.ofMinutes(1))
        ));
        return manager;
    }

    private static CaffeineCache build(String name, int maxSize, Duration ttl) {
        return new CaffeineCache(name,
                Caffeine.newBuilder()
                        .maximumSize(maxSize)
                        .expireAfterWrite(ttl)
                        .recordStats()          // exposes hit/miss ratio via Actuator
                        .build());
    }
}
