package app.dexcode.trustdesk.controllers;

import app.dexcode.trustdesk.entities.EvalRun;
import app.dexcode.trustdesk.services.EvalRunService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
public class EvalRunController {

    private final EvalRunService evalRunService;

    public EvalRunController(EvalRunService evalRunService) {
        this.evalRunService = evalRunService;
    }

    @PostMapping("/eval-runs")
    public EvalRun runEval() {
        return evalRunService.runEval();
    }

    @GetMapping("/eval-runs")
    public List<EvalRun> listEvalRuns() {
        return evalRunService.listEvalRuns();
    }

    @GetMapping("/eval-runs/{id}")
    public ResponseEntity<EvalRun> getEvalRun(@PathVariable String id) {
        try {
            return ResponseEntity.ok(evalRunService.getEvalRun(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
