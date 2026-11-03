package io.github.team404.tikitaka.booking.controller;

import io.github.team404.tikitaka.booking.entity.Reservation;
import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import io.github.team404.tikitaka.booking.service.ReservationService;
import io.github.team404.tikitaka.global.security.jwt.JwtAuthenticationFilter;
import io.github.team404.tikitaka.performanceseat.service.WaitingQueueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 게이트를 끈 경우(k6 부하테스트 등) 대기열 통과 여부를 조회하지 않고 예매가 진행되는지 확인한다.
@WebMvcTest(controllers = ReservationController.class, properties = "tikitaka.booking.queue-gate-enabled=false")
@AutoConfigureMockMvc(addFilters = false)
class ReservationControllerQueueGateDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private WaitingQueueService waitingQueueService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void 게이트가_꺼져_있으면_대기열_통과_여부와_무관하게_예약을_생성한다() throws Exception {
        Reservation reservation = mock(Reservation.class);
        given(reservation.getId()).willReturn(1L);
        given(reservation.getStatus()).willReturn(ReservationStatus.PENDING_PAYMENT);
        given(reservationService.createReservation(any())).willReturn(reservation);

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":10,\"simulationId\":30,\"scheduleId\":20,\"seatIds\":[100]}"))
                .andExpect(status().isCreated());

        verify(waitingQueueService, never()).isAdmitted(anyLong(), anyLong());
    }
}
