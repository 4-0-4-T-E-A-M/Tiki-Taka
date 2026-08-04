package io.github.team404.tikitaka.performanceseat.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WaitingQueueRepository {

    private final StringRedisTemplate redisTemplate;

    // 대기열 회차별 진입 순번을 담는 ZSET
    private String entriesKey(Long scheduleId) {
        return "queue:schedule:%d:entries".formatted(scheduleId);
    }

    // score 발급용 원자 카운터 — 타임스탬프 대신 INCR을 쓰는 이유는 WaitingQueueService에 기록
    private String counterKey(Long scheduleId) {
        return "queue:schedule:%d:counter".formatted(scheduleId);
    }

    // ZADD NX: 이미 대기열에 있는 사용자는 score를 덮어쓰지 않고 기존 순번을 유지한다
    public boolean addIfAbsent(Long scheduleId, Long userId) {
        long score = redisTemplate.opsForValue().increment(counterKey(scheduleId));
        Boolean added = redisTemplate.opsForZSet().addIfAbsent(entriesKey(scheduleId), userId.toString(), score);
        return Boolean.TRUE.equals(added);
    }

    public Long rank(Long scheduleId, Long userId) {
        return redisTemplate.opsForZSet().rank(entriesKey(scheduleId), userId.toString());
    }

    public Long size(Long scheduleId) {
        return redisTemplate.opsForZSet().zCard(entriesKey(scheduleId));
    }
}
