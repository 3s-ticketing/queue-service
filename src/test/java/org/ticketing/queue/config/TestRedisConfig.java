package org.ticketing.queue.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.mockito.BDDMockito.given;
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
        RedisConnectionFactory mock = Mockito.mock(RedisConnectionFactory.class);
        // RedisConfig의 redisTemplate이 connection() 호출하므로 NPE 방지
        RedisConnection mockConnection = Mockito.mock(RedisConnection.class);
        given(mock.getConnection()).willReturn(mockConnection);
        return mock;
    }

    // RedisTemplate Mock으로 대체
    @Bean
    @Primary
    public RedisTemplate<String, String> redisTemplate() {
        return mock(RedisTemplate.class);
    }
}
