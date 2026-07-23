package app.dexcode.trustdesk.controllers;

import app.dexcode.trustdesk.client.AiServiceClient;
import app.dexcode.trustdesk.dto.TriageRequest;
import app.dexcode.trustdesk.dto.TriageResponse;
import app.dexcode.trustdesk.repositories.AgentRunTraceRepository;
import app.dexcode.trustdesk.repositories.TicketRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.seed.data-dir=../data")
class TriageControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private AgentRunTraceRepository agentRunTraceRepository;
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
    void triagePersistsResultOnTicketAndWritesTrace() throws Exception {
        when(aiServiceClient.triage(any(TriageRequest.class))).thenReturn(new TriageResponse(
            "refund", "medium", "frustrated", false,
            "Damaged item reported within return window.", false, null));

        mockMvc.perform(post("/tickets/tkt_9001/triage")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.category").value("refund"))
            .andExpect(jsonPath("$.should_escalate").value(false));

        var ticket = ticketRepository.findById("tkt_9001").orElseThrow();
        Assertions.assertEquals("refund", ticket.getCategory());
        Assertions.assertEquals(Boolean.FALSE, ticket.getShouldEscalate());

        var traces = agentRunTraceRepository.findAll();
        Assertions.assertTrue(traces.stream().anyMatch(
            t -> "tkt_9001".equals(t.getTicketId()) && "triage".equals(t.getRunType())));
    }

    @Test
    void triageRejectsMissingToken() throws Exception {
        mockMvc.perform(post("/tickets/tkt_9001/triage"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void triageReturns404ForUnknownTicket() throws Exception {
        mockMvc.perform(post("/tickets/does-not-exist/triage")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }
}
