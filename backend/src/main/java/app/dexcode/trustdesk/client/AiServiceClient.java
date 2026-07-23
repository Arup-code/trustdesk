package app.dexcode.trustdesk.client;

import app.dexcode.trustdesk.dto.TriageRequest;
import app.dexcode.trustdesk.dto.TriageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiServiceClient {

    private final RestClient restClient;

    public AiServiceClient(@Value("${app.ai-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public TriageResponse triage(TriageRequest request) {
        return restClient.post()
            .uri("/internal/triage")
            .body(request)
            .retrieve()
            .body(TriageResponse.class);
    }
}
