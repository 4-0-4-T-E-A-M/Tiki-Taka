package io.github.team404.tikitaka.performanceseat.service;

import io.github.team404.tikitaka.booking.repository.ReservationRepository;
import io.github.team404.tikitaka.global.kafka.event.PerformanceChangedEvent;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceCreateRequest;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceDetailResponse;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceUpdateRequest;
import io.github.team404.tikitaka.performanceseat.dto.ScheduleCreateRequest;
import io.github.team404.tikitaka.performanceseat.dto.ScheduleResponse;
import io.github.team404.tikitaka.performanceseat.dto.SectionCreateRequest;
import io.github.team404.tikitaka.performanceseat.dto.SeatRowRequest;
import io.github.team404.tikitaka.performanceseat.entity.Performance;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceSchedule;
import io.github.team404.tikitaka.performanceseat.entity.Seat;
import io.github.team404.tikitaka.performanceseat.entity.Section;
import io.github.team404.tikitaka.performanceseat.ranking.PopularPerformanceRankingService;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceCacheRepository;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceRepository;
import io.github.team404.tikitaka.performanceseat.repository.PerformanceScheduleRepository;
import io.github.team404.tikitaka.performanceseat.repository.SeatRepository;
import io.github.team404.tikitaka.performanceseat.repository.SectionRepository;
import io.github.team404.tikitaka.performanceseat.search.PerformanceEventPublisher;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final PerformanceRepository performanceRepository;
    private final PerformanceScheduleRepository performanceScheduleRepository;
    private final SectionRepository sectionRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final PerformanceCacheRepository performanceCacheRepository;
    private final PerformanceEventPublisher performanceEventPublisher;
    private final PopularPerformanceRankingService popularPerformanceRankingService;

    @Transactional
    public Performance createPerformance(PerformanceCreateRequest request) {
        Performance performance = Performance.builder()
                .title(request.title())
                .artist(request.artist())
                .venueName(request.venueName())
                .region(request.region())
                .genre(request.genre())
                .description(request.description())
                .posterUrl(request.posterUrl())
                .build();
        performanceRepository.save(performance);

        List<ScheduleCreateRequest> schedules = request.schedules();
        if (schedules != null) {
            for (ScheduleCreateRequest scheduleRequest : schedules) {
                createSchedule(performance.getId(), scheduleRequest);
            }
        }

        publishAfterCommit(PerformanceChangedEvent.upsert(performance.getId()));
        return performance;
    }

    private void createSchedule(Long performanceId, ScheduleCreateRequest scheduleRequest) {
        PerformanceSchedule schedule = PerformanceSchedule.builder()
                .performanceId(performanceId)
                .performanceDatetime(scheduleRequest.performanceDatetime())
                .openAt(scheduleRequest.openAt())
                .build();
        performanceScheduleRepository.save(schedule);

        List<SectionCreateRequest> sections = scheduleRequest.sections();
        if (sections != null) {
            for (SectionCreateRequest sectionRequest : sections) {
                createSection(schedule.getId(), sectionRequest);
            }
        }
    }

    private void createSection(Long scheduleId, SectionCreateRequest sectionRequest) {
        List<SeatRowRequest> rows = sectionRequest.rows();
        int totalSeatCount = rows == null ? 0 : rows.stream().mapToInt(SeatRowRequest::seatCount).sum();

        Section section = Section.builder()
                .scheduleId(scheduleId)
                .name(sectionRequest.name())
                .totalSeatCount(totalSeatCount)
                .build();
        sectionRepository.save(section);

        if (rows != null) {
            for (SeatRowRequest row : rows) {
                for (int seatNumber = 1; seatNumber <= row.seatCount(); seatNumber++) {
                    seatRepository.save(Seat.builder()
                            .sectionId(section.getId())
                            .rowName(row.rowName())
                            .seatNumber(seatNumber)
                            .grade(row.grade())
                            .price(row.price())
                            .build());
                }
            }
        }
    }

    // 캐시는 TTL을 길게 가져가는 대신(이슈 #57) 원본이 바뀌는 시점에 즉시 무효화해 최신성을 보장한다.
    @Transactional
    public Performance updatePerformance(Long performanceId, PerformanceUpdateRequest request) {
        Performance performance = getPerformance(performanceId);
        performance.update(
                request.title(),
                request.artist(),
                request.venueName(),
                request.region(),
                request.genre(),
                request.description(),
                request.posterUrl());
        performanceCacheRepository.evictDetail(performanceId);
        publishAfterCommit(PerformanceChangedEvent.upsert(performanceId));
        return performance;
    }

    @Transactional
    public void deletePerformance(Long performanceId) {
        Performance performance = getPerformance(performanceId);

        List<Long> scheduleIds = performanceScheduleRepository.findAllByPerformanceId(performanceId).stream()
                .map(PerformanceSchedule::getId)
                .toList();

        if (!scheduleIds.isEmpty() && reservationRepository.existsByScheduleIdIn(scheduleIds)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "예매가 존재하는 공연은 삭제할 수 없습니다.");
        }

        List<Long> sectionIds = sectionRepository.findAllByScheduleIdIn(scheduleIds).stream()
                .map(Section::getId)
                .toList();

        seatRepository.deleteAllBySectionIdIn(sectionIds);
        sectionRepository.deleteAllByScheduleIdIn(scheduleIds);
        performanceScheduleRepository.deleteAllByPerformanceId(performanceId);
        performanceRepository.delete(performance);
        performanceCacheRepository.evictDetail(performanceId);
        publishAfterCommit(PerformanceChangedEvent.delete(performanceId));
    }

    // 검색 인덱스 동기화 이벤트는 커밋 이후에 발행한다 — 롤백된 트랜잭션의 변경을 색인에 흘리지 않기 위함.
    // 관리되는 트랜잭션이 없으면(테스트 등) 즉시 발행으로 폴백한다.
    private void publishAfterCommit(PerformanceChangedEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    performanceEventPublisher.publish(event);
                }
            });
        } else {
            performanceEventPublisher.publish(event);
        }
    }

    @Transactional(readOnly = true)
    public Performance getPerformance(Long performanceId) {
        return performanceRepository.findById(performanceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "공연을 찾을 수 없습니다: " + performanceId));
    }

    @Transactional(readOnly = true)
    public List<PerformanceSchedule> getSchedules(Long performanceId) {
        return performanceScheduleRepository.findAllByPerformanceId(performanceId);
    }

    @Transactional(readOnly = true)
    public List<Performance> listPerformances() {
        return performanceRepository.findAll();
    }

    // 공연 상세 조회 Cache-Aside 진입점. 공연 오픈 시점 동시 상세 조회 트래픽이 대상이라 회차 목록까지
    // 묶어 캐싱한다(캐시 대상 범위 근거는 이슈 #56 참고).
    @Transactional(readOnly = true)
    public PerformanceDetailResponse getPerformanceDetail(Long performanceId) {
        PerformanceDetailResponse detail = performanceCacheRepository.findDetail(performanceId)
                .orElseGet(() -> loadAndCacheDetail(performanceId));
        // 인기 랭킹 조회수 반영 — 캐시 히트 여부와 무관하게 "상세 진입"마다 카운트한다 (#94).
        popularPerformanceRankingService.recordView(performanceId);
        return detail;
    }

    private PerformanceDetailResponse loadAndCacheDetail(Long performanceId) {
        Performance performance = getPerformance(performanceId);
        List<ScheduleResponse> schedules = getSchedules(performanceId).stream()
                .map(ScheduleResponse::from)
                .toList();
        PerformanceDetailResponse detail = PerformanceDetailResponse.of(performance, schedules);
        performanceCacheRepository.saveDetail(performanceId, detail);
        return detail;
    }
}
