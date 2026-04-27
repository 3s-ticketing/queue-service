package org.ticketing.queue.infrastructure.persistence;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.ticketing.queue.domain.model.QueueToken;
import org.ticketing.queue.domain.service.QueueTokenDomainService;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QueueTokenDomainServiceImpl implements QueueTokenDomainService {

    private static final long TOKEN_EXPIRE_MINUTES = 10L;

    @Value("${queue.token.secret}")
    private String secretKey;

    @Override
    public QueueToken issue(UUID matchId, UUID userId) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        LocalDateTime expiredAt = LocalDateTime.now().plusMinutes(TOKEN_EXPIRE_MINUTES);

        String jwt = Jwts.builder()
                .claim("matchId",matchId.toString())
                .claim("userId",userId.toString())
                .claim("type","QUEUE_PASS")
                .setExpiration(Date.from(expiredAt.atZone(ZoneId.systemDefault()).toInstant()))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        return QueueToken.of(matchId, userId, jwt, expiredAt);
    }
}