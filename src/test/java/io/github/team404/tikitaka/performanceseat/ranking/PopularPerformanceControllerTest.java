package io.github.team404.tikitaka.performanceseat.ranking;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.team404.tikitaka.global.security.jwt.JwtAuthenticationFilter;
import io.github.team404.tikitaka.performanceseat.dto.PerformanceResponse;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceGenre;
import io.github.team404.tikitaka.performanceseat.entity.PerformanceRegion;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PopularPerformanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class PopularPerformanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PopularPerformanceRankingService rankingService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private PerformanceResponse response(long id) {
        return new PerformanceResponse(id, "공연 " + id, "아티스트", "체조경기장",
                PerformanceRegion.SEOUL, PerformanceGenre.CONCERT, null, null,
                LocalDateTime.of(2026, 9, 1, 12, 0));
    }

    @Test
    void 인기_공연을_공통_성공_응답으로_반환한다() throws Exception {
        given(rankingService.topPerformances(10)).willReturn(List.of(response(3), response(1)));

        mockMvc.perform(get("/api/performances/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("인기 공연 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data[0].id").value(3))
                .andExpect(jsonPath("$.data[1].id").value(1));
    }

    @Test
    void limit_기본값은_10이다() throws Exception {
        given(rankingService.topPerformances(anyInt())).willReturn(List.of());

        mockMvc.perform(get("/api/performances/popular")).andExpect(status().isOk());

        verify(rankingService).topPerformances(10);
    }

    @Test
    void limit은_1과_50_사이로_보정된다() throws Exception {
        given(rankingService.topPerformances(anyInt())).willReturn(List.of());

        mockMvc.perform(get("/api/performances/popular").param("limit", "999")).andExpect(status().isOk());
        mockMvc.perform(get("/api/performances/popular").param("limit", "0")).andExpect(status().isOk());

        verify(rankingService).topPerformances(50);
        verify(rankingService).topPerformances(1);
    }
}
