package org.ticketing.queue.infrastructure.util;

import org.springframework.data.redis.core.script.DefaultRedisScript;

public class RuaScript {

    public static final DefaultRedisScript<Long> ACQUIRE_SLOT_AND_TOKEN_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local tokenKey = KEYS[1]
                    local availableKey = KEYS[2]
                    
                    -- 이미 토큰 키가 존재하면 슬롯 획득 없이 상태만 반환
                    local existing = redis.call('GET', tokenKey)
                    if existing then
                        if existing == ARGV[2] then
                            return -3  -- PLACEHOLDER: 다른 스레드 발급 중
                        else
                            return -4  -- 이미 발급 완료된 토큰 존재
                        end
                    end
                    
                    -- 슬롯 확인
                    local current = redis.call('GET', availableKey)
                    if not current then
                        return -2  -- 슬롯 미초기화
                    end
                    
                    current = tonumber(current)
                    if current <= 0 then
                        return -1  -- 슬롯 없음
                    end
                    
                    -- 슬롯 차감 + PLACEHOLDER 세팅 (원자적)
                    redis.call('DECR', availableKey)
                    redis.call('SET', tokenKey, ARGV[2], 'EX', ARGV[1])
                    return 1  -- 획득 성공
                    """,
                    Long.class
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
