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

    // 입장 허용 인원 — 오픈 스케줄러(#75)가 오픈 시점에 초기값을 설정한다. 이 키가 없으면(=오픈 전)
    // 아무도 입장 허용되지 않는다. 점진적 증가(ramp-up)는 7주차 안정성 이슈로 미룬다.
    private String admitCountKey(Long scheduleId) {
        return "queue:schedule:%d:admit-count".formatted(scheduleId);
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

    // SETNX: 오픈이 중복 실행되거나(스케줄러 재시도) 재오픈되어도 이미 설정된 입장 허용 인원을 덮지 않는다.
    public void initAdmitCount(Long scheduleId, long initialCount) {
        redisTemplate.opsForValue().setIfAbsent(admitCountKey(scheduleId), Long.toString(initialCount));
    }

    // 아직 설정되지 않았으면(오픈 전) 0.
    public long admitCount(Long scheduleId) {
        String value = redisTemplate.opsForValue().get(admitCountKey(scheduleId));
        return value == null ? 0L : Long.parseLong(value);
    }
}
