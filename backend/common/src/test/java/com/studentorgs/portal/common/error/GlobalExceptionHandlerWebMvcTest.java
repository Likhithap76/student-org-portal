package com.studentorgs.portal.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;

@WebMvcTest(GlobalExceptionHandlerWebMvcTest.FailingController.class)
@ActiveProfiles("test")
@Import(GlobalExceptionHandlerWebMvcTest.FailingController.class)
@WithMockUser
class GlobalExceptionHandlerWebMvcTest {

    record Body(@NotBlank String title) {
    }

    @RestController
    @Validated
    static class FailingController {

        @GetMapping("/fail/{kind}")
        String fail(@PathVariable String kind) {
            throw switch (kind) {
                case "not-found" -> new NotFoundException("Event 9 not found");
                case "conflict" -> new ConflictException("Email already registered");
                case "forbidden" -> new ForbiddenOperationException("Only the publisher may delete");
                case "rule" -> new BusinessRuleException("Event already started");
                case "unique" -> new DataIntegrityViolationException("x", new SQLException("dup", "23505"));
                case "fk" -> new DataIntegrityViolationException("x", new SQLException("fk", "23503"));
                case "check" -> new DataIntegrityViolationException("x", new SQLException("chk", "23514"));
                case "bad-credentials" -> new BadCredentialsException("bad");
                case "unavailable" -> new ServiceUnavailableException("User service unavailable", null);
                case "db-down" -> new org.springframework.transaction.CannotCreateTransactionException("no connection");
                default -> new IllegalStateException("boom");
            };
        }

        @PostMapping("/body")
        String body(@Valid @RequestBody Body body) {
            return body.title();
        }

        @GetMapping("/param")
        int param(@RequestParam @Max(100) int size) {
            return size;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void notFoundException_returns404Problem() throws Exception {
        mockMvc.perform(get("/fail/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Event 9 not found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void conflictException_returns409() throws Exception {
        mockMvc.perform(get("/fail/conflict")).andExpect(status().isConflict());
    }

    @Test
    void forbiddenOperationException_returns403() throws Exception {
        mockMvc.perform(get("/fail/forbidden")).andExpect(status().isForbidden());
    }

    @Test
    void businessRuleException_returns422() throws Exception {
        mockMvc.perform(get("/fail/rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Event already started"));
    }

    @Test
    void uniqueViolation_returns409WithReadableMessage() throws Exception {
        mockMvc.perform(get("/fail/unique"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A record with the same unique value already exists"));
    }

    @Test
    void foreignKeyViolation_returns409() throws Exception {
        mockMvc.perform(get("/fail/fk")).andExpect(status().isConflict());
    }

    @Test
    void checkViolation_returns422() throws Exception {
        mockMvc.perform(get("/fail/check")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void badCredentials_returns401GenericMessage() throws Exception {
        mockMvc.perform(get("/fail/bad-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void serviceUnavailable_returns503() throws Exception {
        mockMvc.perform(get("/fail/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("User service unavailable"));
    }

    @Test
    void databaseUnreachable_returns503() throws Exception {
        mockMvc.perform(get("/fail/db-down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Database unavailable. Please try again"));
    }

    @Test
    void unexpectedException_returns500WithoutInternals() throws Exception {
        mockMvc.perform(get("/fail/other"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void invalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void malformedBody_returns400Problem() throws Exception {
        mockMvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unknownPath_returns404ProblemWithTimestamp() throws Exception {
        mockMvc.perform(get("/no/such/path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void wrongMethod_returns405Problem() throws Exception {
        mockMvc.perform(post("/param"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void invalidParam_returns400WithErrors() throws Exception {
        mockMvc.perform(get("/param").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }
}
