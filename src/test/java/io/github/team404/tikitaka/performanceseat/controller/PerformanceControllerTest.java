package io.github.team404.tikitaka.performanceseat.controller;

import io.github.team404.tikitaka.global.security.jwt.JwtAuthenticationFilter;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceDetailResponse;
import io.github.team404.tikitaka.performanceseat.dto.ScheduleResponse;
import io.github.team404.tikitaka.performanceseat.entity.Performance;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceSchedule;
import io.github.team404.tikitaka.performanceseat.entity.ScheduleStatus;
import io.github.team404.tikitaka.performanceseat.service.PerformanceService;
import java.time.LocalDateTime;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PerformanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class PerformanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PerformanceService performanceService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void 공연을_생성하고_공통_성공_응답으로_반환한다() throws Exception {
        Performance performance = performance(1L, "공연 제목");
        given(performanceService.createPerformance(any())).willReturn(performance);

        mockMvc.perform(post("/api/performances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(performanceCreateRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("공연 생성에 성공했습니다."))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("공연 제목"))
                .andExpect(jsonPath("$.data.region").value("SEOUL"))
                .andExpect(jsonPath("$.data.genre").value("CONCERT"))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void 공연을_수정하고_공통_성공_응답으로_반환한다() throws Exception {
        Performance performance = performance(1L, "수정된 공연");
        given(performanceService.updatePerformance(org.mockito.ArgumentMatchers.eq(1L), any()))
                .willReturn(performance);

        mockMvc.perform(put("/api/performances/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(performanceUpdateRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("공연 수정에 성공했습니다."))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("수정된 공연"))
                .andExpect(jsonPath("$.data.region").value("SEOUL"))
                .andExpect(jsonPath("$.data.genre").value("CONCERT"))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void 공연_목록을_공통_성공_응답으로_조회한다() throws Exception {
        Performance performance = performance(1L, "공연 제목");
        given(performanceService.listPerformances()).willReturn(List.of(performance));

        mockMvc.perform(get("/api/performances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("공연 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].title").value("공연 제목"))
                .andExpect(jsonPath("$.data[0].region").value("SEOUL"))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void 공연_상세를_공통_성공_응답으로_조회한다() throws Exception {
        Performance performance = performance(1L, "공연 제목");
        PerformanceSchedule schedule = schedule(11L);
        PerformanceDetailResponse detail = PerformanceDetailResponse.of(performance, List.of(ScheduleResponse.from(schedule)));
        given(performanceService.getPerformanceDetail(1L)).willReturn(detail);

        mockMvc.perform(get("/api/performances/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("공연 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.performance.id").value(1))
                .andExpect(jsonPath("$.data.performance.title").value("공연 제목"))
                .andExpect(jsonPath("$.data.schedules").isArray())
                .andExpect(jsonPath("$.data.schedules[0].id").value(11))
                .andExpect(jsonPath("$.data.schedules[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.performance").doesNotExist());
    }

    @Test
    void 공연을_삭제하고_데이터_없는_공통_성공_응답을_반환한다() throws Exception {
        mockMvc.perform(delete("/api/performances/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("공연 삭제에 성공했습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(performanceService).deletePerformance(1L);
    }

    private Performance performance(Long id, String title) {
        Performance performance = mock(Performance.class);
        given(performance.getId()).willReturn(id);
        given(performance.getTitle()).willReturn(title);
        given(performance.getArtist()).willReturn("아티스트");
        given(performance.getVenueName()).willReturn("공연장");
        given(performance.getRegion()).willReturn(PerformanceRegion.SEOUL);
        given(performance.getGenre()).willReturn(PerformanceGenre.CONCERT);
        given(performance.getDescription()).willReturn("설명");
        given(performance.getPosterUrl()).willReturn("https://example.com/poster.jpg");
        given(performance.getCreatedAt()).willReturn(LocalDateTime.of(2026, 8, 7, 12, 0));
        return performance;
    }

    private PerformanceSchedule schedule(Long id) {
        PerformanceSchedule schedule = mock(PerformanceSchedule.class);
        given(schedule.getId()).willReturn(id);
        given(schedule.getPerformanceDatetime()).willReturn(LocalDateTime.of(2026, 9, 1, 19, 0));
        given(schedule.getOpenAt()).willReturn(LocalDateTime.of(2026, 8, 25, 20, 0));
        given(schedule.getStatus()).willReturn(ScheduleStatus.SCHEDULED);
        return schedule;
    }

    private String performanceCreateRequest() {
        return """
                {"title":"공연 제목","artist":"아티스트","venueName":"공연장","region":"SEOUL","genre":"CONCERT","description":"설명","posterUrl":"https://example.com/poster.jpg","schedules":[]}
                """;
    }

    private String performanceUpdateRequest() {
        return """
                {"title":"수정된 공연","artist":"아티스트","venueName":"공연장","region":"SEOUL","genre":"CONCERT","description":"수정된 설명","posterUrl":"https://example.com/poster.jpg"}
                """;
    }
}
