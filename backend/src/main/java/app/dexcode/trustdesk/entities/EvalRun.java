package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.persistence.JsonConverters;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "eval_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class EvalRun {

    @Id
    @Column(name = "eval_run_id")
    @EqualsAndHashCode.Include
    @ToString.Include
    private String evalRunId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "total_cases")
    private int totalCases;

    @Convert(converter = JsonConverters.StringObjectMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> metrics;

    @Convert(converter = JsonConverters.ObjectListConverter.class)
    @Column(name = "case_results", columnDefinition = "TEXT")
    private List<Map<String, Object>> caseResults;
}
