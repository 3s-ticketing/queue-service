package org.ticketing.queue.infrastructure.util;

import org.springframework.data.redis.core.script.DefaultRedisScript;

public class RuaScript {

    public static final DefaultRedisScript<Long> ACQUIRE_SLOT_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local current = redis.call('GET', KEYS[1])
                    
                    if not current then
                        return -2
                    end
    
                    current = tonumber(current)
    
                    if current <= 0 then
                        return -1
                    end
    
                    return redis.call('DECR', KEYS[1])
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
