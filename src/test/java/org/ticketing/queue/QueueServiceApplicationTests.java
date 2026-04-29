package org.ticketing.queue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.ticketing.queue.domain.repository.BannedUserRepository;
import org.ticketing.queue.domain.repository.QueueRedisRepository;
import org.ticketing.queue.infrastructure.persistence.SseEmitterRepository;
import org.ticketing.queue.infrastructure.redis.pubsub.QueueRedisSubscriber;

@SpringBootTest
class QueueServiceApplicationTests {

    // RedisConfig가 RedisConnectionFactory를 찾으므로 MockBean으로 제공
    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private QueueRedisRepository queueRedisRepository;

    @MockitoBean
    private QueueRedisSubscriber queueRedisSubscriber;

    @MockitoBean
    private SseEmitterRepository sseEmitterRepository;

    @MockitoBean
    private BannedUserRepository bannedUserRepository;

    // QueueStreamPublisher가 StringRedisTemplate 주입받음
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    // KafkaConfig가 KafkaTemplate<Object, Object> 주입받음
    @MockitoBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    // Events(공통모듈)용
    @MockitoBean
    private KafkaTemplate<String, Object> kafkaStringTemplate;
    
    @Test
    void contextLoads() {
    }

}
