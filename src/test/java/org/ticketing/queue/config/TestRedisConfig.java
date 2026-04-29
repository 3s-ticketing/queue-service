package org.ticketing.queue.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestRedisConfig {

    // RedisMessageListenerContainer Mock으로 대체
    @Bean
    @Primary
    public RedisMessageListenerContainer redisMessageListenerContainer() {
        return mock(RedisMessageListenerContainer.class);
    }

    // RedisConnectionFactory Mock으로 대체
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return mock(RedisConnectionFactory.class);
    }

    // RedisTemplate Mock으로 대체
    @Bean
    @Primary
    public RedisTemplate<String, String> redisTemplate() {
        return mock(RedisTemplate.class);
    }
}
