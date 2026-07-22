package app.dexcode.trustdesk.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.seed.data-dir=../data")
class TicketControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

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

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + token);
    }

    @Test
    void listsSeededTickets() throws Exception {
        mockMvc.perform(authed(get("/tickets")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.ticketId == 'tkt_9001')]").exists());
    }

    @Test
    void fetchesTicketDetailWithCustomerAndOrder() throws Exception {
        mockMvc.perform(authed(get("/tickets/tkt_9001")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customer.customerId").value("cus_1001"))
            .andExpect(jsonPath("$.order.orderId").value("ord_5001"));
    }

    @Test
    void returns404ForUnknownTicket() throws Exception {
        mockMvc.perform(authed(get("/tickets/does-not-exist")))
            .andExpect(status().isNotFound());
    }
}
