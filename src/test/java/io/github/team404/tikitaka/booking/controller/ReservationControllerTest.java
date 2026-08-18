package io.github.team404.tikitaka.booking.controller;

import io.github.team404.tikitaka.booking.entity.Reservation;
import io.github.team404.tikitaka.booking.entity.ReservationStatus;
import io.github.team404.tikitaka.booking.service.ReservationService;
import io.github.team404.tikitaka.global.security.jwt.JwtAuthenticationFilter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void 예약을_생성하고_공통_성공_응답으로_반환한다() throws Exception {
        Reservation reservation = reservation(1L, 10L, 20L);
        given(reservationService.createReservation(any())).willReturn(reservation);

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":10,\"simulationId\":30,\"scheduleId\":20,\"seatIds\":[100,101]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("예약 생성에 성공했습니다."))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.userId").value(10))
                .andExpect(jsonPath("$.data.scheduleId").value(20))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void 예약_단건을_공통_성공_응답으로_조회한다() throws Exception {
        Reservation reservation = reservation(1L, 10L, 20L);
        given(reservationService.getReservation(1L)).willReturn(reservation);

        mockMvc.perform(get("/api/reservations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("예약 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.userId").value(10))
                .andExpect(jsonPath("$.data.scheduleId").value(20))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void 예약_목록을_공통_성공_응답으로_조회한다() throws Exception {
        Reservation reservation = reservation(1L, 10L, 20L);
        given(reservationService.getReservationsByUser(10L))
                .willReturn(List.of(reservation));

        mockMvc.perform(get("/api/reservations").param("userId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("예약 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(10))
                .andExpect(jsonPath("$.data[0].scheduleId").value(20))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void 예약을_취소하고_데이터_없는_공통_성공_응답을_반환한다() throws Exception {
        mockMvc.perform(post("/api/reservations/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("예약 취소에 성공했습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(reservationService).cancelReservation(1L);
    }

    private Reservation reservation(Long id, Long userId, Long scheduleId) {
        Reservation reservation = mock(Reservation.class);
        given(reservation.getId()).willReturn(id);
        given(reservation.getUserId()).willReturn(userId);
        given(reservation.getSimulationId()).willReturn(30L);
        given(reservation.getScheduleId()).willReturn(scheduleId);
        given(reservation.getSelectedQuantity()).willReturn(2);
        given(reservation.getStatus()).willReturn(ReservationStatus.PENDING_PAYMENT);
        return reservation;
    }
}
