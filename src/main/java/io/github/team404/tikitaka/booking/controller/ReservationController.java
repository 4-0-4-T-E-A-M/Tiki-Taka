package io.github.team404.tikitaka.booking.controller;

import io.github.team404.tikitaka.booking.dto.ReservationCreateRequest;
import io.github.team404.tikitaka.booking.dto.ReservationResponse;
import io.github.team404.tikitaka.booking.service.ReservationService;
import io.github.team404.tikitaka.global.response.BaseResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<BaseResponse<ReservationResponse>> create(@RequestBody ReservationCreateRequest request) {
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
}
