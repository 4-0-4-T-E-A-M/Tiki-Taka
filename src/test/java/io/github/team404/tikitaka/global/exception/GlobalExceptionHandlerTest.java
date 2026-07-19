package io.github.team404.tikitaka.global.exception;

import io.github.team404.tikitaka.TikitakaApplication;
import io.github.team404.tikitaka.user.exception.UserErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GlobalExceptionHandler가 실제 요청/응답을 통해 공통 오류 응답 포맷
 * ({code, message, timestamp})을 만들어내는지 검증한다.
 * Security 경로 접근 제어와는 무관하므로 필터는 비활성화한다.
 * 검증 대상 컨트롤러가 아직 실제로 존재하지 않아, 이 테스트에서만 쓰는 컨트롤러를
 * 추가 소스로 등록해 재현한다(SecurityFilterChainTest와 동일한 방식).
 */
@SpringBootTest(classes = {TikitakaApplication.class, GlobalExceptionHandlerTest.ExceptionTestController.class})
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void BusinessException은_ErrorCode에_정의된_HTTP_상태와_공통_형식으로_응답한다() throws Exception {
        mockMvc.perform(get("/test/exception/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_001"))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void Validation_실패시_400과_공통_입력값_오류_코드를_반환한다() throws Exception {
        mockMvc.perform(post("/test/exception/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"))
                .andExpect(jsonPath("$.message").value("이름은 비어 있을 수 없습니다."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void 잘못된_JSON이면_400과_공통_형식으로_응답한다() throws Exception {
        mockMvc.perform(post("/test/exception/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void 지원하지_않는_HTTP_메서드면_405와_공통_형식으로_응답한다() throws Exception {
        mockMvc.perform(get("/test/exception/validate"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON_003"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void 예상하지_못한_예외는_500과_일반화된_메시지만_반환하고_내부_정보를_노출하지_않는다() throws Exception {
        mockMvc.perform(get("/test/exception/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_500"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.message", not(containsString("boom"))))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @RestController
    @RequestMapping("/test/exception")
    static class ExceptionTestController {

        @GetMapping("/business")
        public String business() {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        @PostMapping("/validate")
        public String validate(@Valid @RequestBody TestRequest request) {
            return "ok";
        }

        @GetMapping("/unexpected")
        public String unexpected() {
            throw new IllegalStateException("boom: 내부 스택 정보가 노출되면 안 된다");
        }

        record TestRequest(@NotBlank(message = "이름은 비어 있을 수 없습니다.") String name) {
        }
    }
}
