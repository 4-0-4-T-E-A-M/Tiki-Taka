package io.github.team404.tikitaka.booking.controller;

import io.github.team404.tikitaka.booking.dto.ReservationCreateRequest;
import io.github.team404.tikitaka.booking.dto.ReservationResponse;
import io.github.team404.tikitaka.booking.service.ReservationService;
import io.github.team404.tikitaka.global.response.BaseResponse;
import io.github.team404.tikitaka.performanceseat.service.WaitingQueueService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final WaitingQueueService waitingQueueService;
    private final boolean queueGateEnabled;

    public ReservationController(
            ReservationService reservationService,
            WaitingQueueService waitingQueueService,
            @Value("${tikitaka.booking.queue-gate-enabled:true}") boolean queueGateEnabled) {
        this.reservationService = reservationService;
        this.waitingQueueService = waitingQueueService;
        this.queueGateEnabled = queueGateEnabled;
    }

    @PostMapping
    public ResponseEntity<BaseResponse<ReservationResponse>> create(@RequestBody ReservationCreateRequest request) {
        validateAdmitted(request);
        ReservationResponse response = ReservationResponse.from(reservationService.createReservation(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success("예약 생성에 성공했습니다.", response));
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<BaseResponse<ReservationResponse>> get(@PathVariable Long reservationId) {
        ReservationResponse response = ReservationResponse.from(reservationService.getReservation(reservationId));
        return ResponseEntity.ok(BaseResponse.success("예약 조회에 성공했습니다.", response));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<List<ReservationResponse>>> list(@RequestParam Long userId) {
        List<ReservationResponse> responses = reservationService.getReservationsByUser(userId).stream()
                .map(ReservationResponse::from)
                .toList();
        return ResponseEntity.ok(BaseResponse.success("예약 목록 조회에 성공했습니다.", responses));
    }

    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<BaseResponse<Void>> cancel(@PathVariable Long reservationId) {
        reservationService.cancelReservation(reservationId);
        return ResponseEntity.ok(BaseResponse.success("예약 취소에 성공했습니다."));
    }

    // 대기열을 통과(admitted)한 사용자만 예매를 생성할 수 있다(#105).
    // 서비스가 아닌 컨트롤러에 두는 이유: 게이트는 "API 진입 자격" 검증이고, 서비스는 좌석 락·홀드 로직만 책임지게 해
    // ReservationServiceTest·ReservationConcurrencyTest가 대기열 상태와 무관하게 락 동작만 검증하도록 유지한다.
    // admitted 집합은 회차 오픈 이후에만 채워지므로 오픈 전 회차는 이 검증만으로 함께 막힌다.
    // 연습 세션 k6 봇이 게이트를 충족하는 방식(봇 일괄 admitted 처리)이 생기기 전까지는 플래그로 끌 수 있게 둔다.
    private void validateAdmitted(ReservationCreateRequest request) {
        if (!queueGateEnabled) {
            return;
        }
        if (request.scheduleId() == null || request.userId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId와 scheduleId는 필수입니다.");
        }
        if (!waitingQueueService.isAdmitted(request.scheduleId(), request.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "대기열을 통과한 사용자만 예매할 수 있습니다.");
        }
    }
}
