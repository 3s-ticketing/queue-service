package org.ticketing.queue.infrastructure.util;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

public class RuaScript {

    public static final RedisScript<Long> ENTRY_SCRIPT = RedisScript.of("""
    local queueKey = KEYS[1]
    local sequenceKey = KEYS[2]
    local bannedKey = KEYS[3]
    local enteredAtKey = KEYS[4]
    local userId = ARGV[1]
    local now = ARGV[2]
    
    -- 차단된 유저 확인
    if redis.call('EXISTS', bannedKey) == 1 then
        return -1
    end
    
    -- 이미 대기열에 존재하면 0 반환
    if redis.call('ZSCORE', queueKey, userId) then
        return 0
    end
    
    -- sequence 증가 후 ZSet에 추가
    local seq = redis.call('INCR', sequenceKey)
    redis.call('ZADD', queueKey, seq, userId)
    
    -- enteredAt Hash에 저장 (field=userId, value=now)
    redis.call('HSET', enteredAtKey, userId, now)
    
    return 1
    """, Long.class);


    public static final DefaultRedisScript<List> ACQUIRE_SLOT_AND_TOKEN_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local userTokenKey = KEYS[1]
                    local availableKey = KEYS[2]
                    local enteredAtKey = KEYS[3]
                    local userId = ARGV[1]
                    local ttl = ARGV[2]
                    local placeholder = ARGV[3]

                    -- 이미 토큰 키가 존재하면 슬롯 획득 없이 상태만 반환
                    local existing = redis.call('GET', userTokenKey)
                    if existing then
                        if existing == placeholder then
                            return {-3}  -- PENDING: 다른 스레드 발급 중
                        else
                            return {-4}  -- ALREADY_ISSUED: 이미 발급 완료
                        end
                    end

                    -- 유저가 여전히 대기열에 있는지 확인 (슬롯 획득 전에 원자적으로 체크)
                    local enteredAt = redis.call('HGET', enteredAtKey, userId)
                    if not enteredAt then
                        return {-5}  -- USER_NOT_IN_QUEUE: ban/refresh/rollback 등으로 이미 제거됨
                    end

                    -- 슬롯 확인
                    local current = redis.call('GET', availableKey)
                    if not current then
                        return {-2}  -- 슬롯 미초기화
                    end

                    current = tonumber(current)
                    if current <= 0 then
                        return {-1}  -- 슬롯 없음
                    end

                    -- 슬롯 차감 + PLACEHOLDER 세팅 + enteredAt 반환 (원자적)
                    redis.call('DECR', availableKey)
                    redis.call('SET', userTokenKey, placeholder, 'EX', ttl)
                    return {1, enteredAt}  -- SUCCESS
                    """,
                    List.class
            );


    public static final DefaultRedisScript<Long> RELEASE_SLOT_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local current = redis.call('GET', KEYS[1])
                    local max = redis.call('GET', KEYS[2])
    
                    if not current or not max then
                        return -2
                    end
    
                    current = tonumber(current)
                    max = tonumber(max)
    
                    if current >= max then
                        return -1
                    end
    
                    return redis.call('INCR', KEYS[1])
                    """,
                    Long.class
            );
}
