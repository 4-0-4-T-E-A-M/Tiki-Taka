package io.github.team404.tikitaka.performanceseat.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.team404.tikitaka.TikitakaApplication;
import io.github.team404.tikitaka.global.exception.BusinessException;
import io.github.team404.tikitaka.global.security.jwt.JwtTokenProvider;
import io.github.team404.tikitaka.performanceseat.exception.QueueErrorCode;
import io.github.team404.tikitaka.performanceseat.service.WaitingQueueService;
import io.github.team404.tikitaka.user.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = TikitakaApplication.class)
@AutoConfigureMockMvc
class WaitingQueueControllerTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private WaitingQueueService waitingQueueService;

    @Test
    void 대기열에_진입하면_순번과_대기인원을_반환한다() throws Exception {
        // given
        when(waitingQueueService.enter(1L, USER_ID)).thenReturn(0L);
        when(waitingQueueService.size(1L)).thenReturn(1L);

        // when & then
        mockMvc.perform(post("/api/schedules/1/queue")
                        .header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rank").value(0))
                .andExpect(jsonPath("$.waitingCount").value(1));
    }

    @Test
    void 대기열에_없는_사용자가_순번을_조회하면_404를_반환한다() throws Exception {
        // given
        when(waitingQueueService.getRank(1L, USER_ID))
                .thenThrow(new BusinessException(QueueErrorCode.QUEUE_ENTRY_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/schedules/1/queue/me")
                        .header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUEUE_002"));
    }

    @Test
    void 토큰없이_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/schedules/1/queue/me"))
                .andExpect(status().isUnauthorized());
    }

    private String accessToken() {
        return jwtTokenProvider.generateAccessToken(USER_ID, UserRole.USER);
    }
}
