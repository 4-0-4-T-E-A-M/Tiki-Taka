package io.github.team404.tikitaka.performanceseat.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WaitingQueueRepository {

    // ZPOPMIN + SADD를 Redis 서버 안에서 한 번에 실행해 원자성을 보장한다. 둘을 애플리케이션에서
    // 따로 호출하던 이전 구현은 그 사이에 프로세스가 죽으면 사용자가 대기열에서는 빠졌지만 admitted
    // 집합에는 들어가지 못해 영구히 사라질 수 있었다(#117, 이전 코드 주석의 "원자적이지 않다" 메모).
    private static final RedisScript<Long> ADMIT_FRONT_SCRIPT = new DefaultRedisScript<>("""
            local popped = redis.call('ZPOPMIN', KEYS[1], ARGV[1])
            if #popped == 0 then
                return 0
            end
            local count = 0
            for i = 1, #popped, 2 do
                redis.call('SADD', KEYS[2], popped[i])
                count = count + 1
            end
            return count
            """, Long.class);

    // admit-count(목표)·admittedSize(이미 통과한 수) 조회와 admitFront 호출을 세 번의 Redis 왕복으로
    // 나누면, 다중 인스턴스가 동시에 호출할 때 둘 다 같은 slots를 계산해 목표보다 많이 통과시키는
    // 경쟁 조건(overshoot)이 생긴다(#117). 전체를 한 Lua 스크립트로 묶어 Redis의 단일 스레드 실행
    // 모델에 위임하면 호출이 겹쳐도 직렬화되어 목표를 넘지 않는다.
    private static final RedisScript<Long> ADMIT_UP_TO_TARGET_SCRIPT = new DefaultRedisScript<>("""
            local target = redis.call('GET', KEYS[1])
            if not target then
                return 0
            end
            local slots = tonumber(target) - redis.call('SCARD', KEYS[2])
            if slots <= 0 then
                return 0
            end
            local popped = redis.call('ZPOPMIN', KEYS[3], slots)
            if #popped == 0 then
                return 0
            end
            local count = 0
            for i = 1, #popped, 2 do
                redis.call('SADD', KEYS[2], popped[i])
                count = count + 1
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    // 대기열 회차별 진입 순번을 담는 ZSET
    private String entriesKey(Long scheduleId) {
        return "queue:schedule:%d:entries".formatted(scheduleId);
    }

    // score 발급용 원자 카운터 — 타임스탬프 대신 INCR을 쓰는 이유는 WaitingQueueService에 기록
    private String counterKey(Long scheduleId) {
        return "queue:schedule:%d:counter".formatted(scheduleId);
    }

    // 입장 허용 인원(누적 목표) — 공연 오픈 스케줄러(#75)가 오픈 시점에 초기값을 설정한다. 이 키가 없으면(=오픈 전)
    // 아무도 입장 허용되지 않는다. 점진적 증가(ramp-up)는 7주차 안정성 이슈로 미룬다.
    private String admitCountKey(Long scheduleId) {
        return "queue:schedule:%d:admit-count".formatted(scheduleId);
    }

    // 대기열을 통과한(admitted) 사용자 집합. 대기열 ZSET에서 빠져나온 사용자가 여기로 들어온다(#102).
    private String admittedKey(Long scheduleId) {
        return "queue:schedule:%d:admitted".formatted(scheduleId);
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

    public long admittedSize(Long scheduleId) {
        Long size = redisTemplate.opsForSet().size(admittedKey(scheduleId));
        return size == null ? 0L : size;
    }

    public boolean isAdmitted(Long scheduleId, Long userId) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForSet().isMember(admittedKey(scheduleId), userId.toString()));
    }

    // 대기열 앞에서 count명을 꺼내 admitted 집합으로 옮긴다. 실제로 옮긴 수를 반환.
    // Lua 스크립트(ADMIT_FRONT_SCRIPT)로 원자 실행한다(#117).
    public int admitFront(Long scheduleId, long count) {
        if (count <= 0) {
            return 0;
        }
        Long admitted = redisTemplate.execute(ADMIT_FRONT_SCRIPT,
                List.of(entriesKey(scheduleId), admittedKey(scheduleId)),
                Long.toString(count));
        return admitted == null ? 0 : admitted.intValue();
    }

    // admit-count 목표에 아직 못 미친 만큼만 대기열 앞에서 통과시킨다. 목표 조회 → 남은 자리 계산 →
    // 통과 처리를 하나의 Lua 스크립트로 원자 실행해 다중 인스턴스 동시 호출에도 목표를 넘지 않는다
    // (WaitingQueueService.admitDueUsers에서 사용, #117).
    public int admitUpToTarget(Long scheduleId) {
        Long admitted = redisTemplate.execute(ADMIT_UP_TO_TARGET_SCRIPT,
                List.of(admitCountKey(scheduleId), admittedKey(scheduleId), entriesKey(scheduleId)));
        return admitted == null ? 0 : admitted.intValue();
    }
}
