package app.dexcode.trustdesk.client;

import app.dexcode.trustdesk.dto.DraftRequest;
import app.dexcode.trustdesk.dto.DraftResponse;
import app.dexcode.trustdesk.dto.TriageRequest;
import app.dexcode.trustdesk.dto.TriageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiServiceClient {

    private final RestClient restClient;
    private final String internalKey;

    public AiServiceClient(
        @Value("${app.ai-service.base-url}") String baseUrl,
        @Value("${app.ai-service.internal-key}") String internalKey
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.internalKey = internalKey;
    }

    public TriageResponse triage(TriageRequest request) {
        return restClient.post()
            .uri("/internal/triage")
            .header("X-Internal-Key", internalKey)
            .body(request)
            .retrieve()
            .body(TriageResponse.class);
    }

    public DraftResponse draft(DraftRequest request) {
        return restClient.post()
            .uri("/internal/draft")
            .header("X-Internal-Key", internalKey)
            .body(request)
            .retrieve()
            .body(DraftResponse.class);
    }
}
