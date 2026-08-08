package app.dexcode.trustdesk.services;

import app.dexcode.trustdesk.client.AiServiceClient;
import app.dexcode.trustdesk.dto.EvalRunResult;
import app.dexcode.trustdesk.entities.EvalRun;
import app.dexcode.trustdesk.repositories.EvalRunRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class EvalRunService {

    private final AiServiceClient aiServiceClient;
    private final EvalRunRepository evalRunRepository;

    public EvalRunService(AiServiceClient aiServiceClient, EvalRunRepository evalRunRepository) {
        this.aiServiceClient = aiServiceClient;
        this.evalRunRepository = evalRunRepository;
    }

    public EvalRun runEval() {
        Instant startedAt = Instant.now();
        EvalRunResult result = aiServiceClient.runEval();

        EvalRun evalRun = EvalRun.builder()
            .evalRunId(UUID.randomUUID().toString())
            .startedAt(startedAt)
            .completedAt(Instant.now())
            .totalCases(result.totalCases())
            .metrics(result.metrics())
            .caseResults(result.caseResults())
            .build();
        return evalRunRepository.save(evalRun);
    }

    public List<EvalRun> listEvalRuns() {
        return evalRunRepository.findAll();
    }

    public EvalRun getEvalRun(String evalRunId) {
        return evalRunRepository.findById(evalRunId)
            .orElseThrow(() -> new NoSuchElementException("Eval run not found: " + evalRunId));
    }
}
