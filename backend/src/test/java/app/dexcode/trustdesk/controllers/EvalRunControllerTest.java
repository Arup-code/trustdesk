package app.dexcode.trustdesk.controllers;

import app.dexcode.trustdesk.client.AiServiceClient;
import app.dexcode.trustdesk.dto.EvalRunResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EvalRunControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private AiServiceClient aiServiceClient;

    private String token;

    @BeforeEach
    void login() throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new AuthController.LoginRequest("agent1", "agent123"))))
            .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void runEvalPersistsAndReturnsEvalRun() throws Exception {
        when(aiServiceClient.runEval()).thenReturn(new EvalRunResult(
            8,
            Map.of("triage_accuracy", 0.875, "priority_accuracy", 0.75,
                "citation_coverage", 1.0, "unsafe_action_block_rate", 1.0,
                "escalation_accuracy", 1.0),
            List.of(Map.of("case_id", "eval_001", "category_match", true))));

        String body = mockMvc.perform(post("/eval-runs")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCases").value(8))
            .andReturn().getResponse().getContentAsString();

        String evalRunId = objectMapper.readTree(body).get("evalRunId").asText();

        mockMvc.perform(get("/eval-runs/" + evalRunId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCases").value(8));

        mockMvc.perform(get("/eval-runs")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.evalRunId == '" + evalRunId + "')]").exists());
    }

    @Test
    void getEvalRunReturns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/eval-runs/does-not-exist")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void runEvalRejectsMissingToken() throws Exception {
        mockMvc.perform(post("/eval-runs"))
            .andExpect(status().isUnauthorized());
    }
}
