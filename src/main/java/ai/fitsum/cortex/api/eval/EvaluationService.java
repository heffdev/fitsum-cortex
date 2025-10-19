package ai.fitsum.cortex.api.eval;

import ai.fitsum.cortex.api.domain.EvalCase;
import ai.fitsum.cortex.api.domain.EvalResult;
import ai.fitsum.cortex.api.domain.EvalRun;
import ai.fitsum.cortex.api.retrieval.HybridRetriever;
import ai.fitsum.cortex.api.retrieval.RetrievedChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Evaluation framework for measuring retrieval quality.
 * Metrics: Precision@5, MRR (Mean Reciprocal Rank), Faithfulness.
 */
@Service
public class EvaluationService {
    
    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);
    
    private final HybridRetriever retriever;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    
    public EvaluationService(
        HybridRetriever retriever,
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper
    ) {
        this.retriever = retriever;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }
    
    public EvalRun runEvaluation(String configDescription) {
        log.info("Starting evaluation run: {}", configDescription);
        
        // Create run record
        ObjectNode config = objectMapper.createObjectNode();
        config.put("description", configDescription);
        config.put("timestamp", LocalDateTime.now().toString());
        
        Long runId = jdbcTemplate.queryForObject(
            "INSERT INTO eval_run (config_json, started_at) VALUES (?::jsonb, NOW()) RETURNING id",
            Long.class,
            config.toString()
        );
        
        // Load all eval cases
        List<EvalCase> cases = loadEvalCases();
        log.info("Running evaluation on {} cases", cases.size());
        
        List<EvalMetrics> results = new ArrayList<>();
        
        for (EvalCase evalCase : cases) {
            EvalMetrics metrics = evaluateCase(runId, evalCase);
            results.add(metrics);
        }
        
        // Calculate aggregate metrics
        double avgPrecisionAt5 = results.stream()
            .mapToDouble(EvalMetrics::precisionAt5)
            .average()
            .orElse(0.0);
        
        double avgMrr = results.stream()
            .mapToDouble(EvalMetrics::mrr)
            .average()
            .orElse(0.0);
        
        // Update run with results
        jdbcTemplate.update(
            """
            UPDATE eval_run 
            SET completed_at = NOW(),
                precision_at_5 = ?,
                mrr = ?
            WHERE id = ?
            """,
            BigDecimal.valueOf(avgPrecisionAt5).setScale(4, RoundingMode.HALF_UP),
            BigDecimal.valueOf(avgMrr).setScale(4, RoundingMode.HALF_UP),
            runId
        );
        
        log.info("Evaluation complete: P@5={}, MRR={}", avgPrecisionAt5, avgMrr);
        
        return new EvalRun(
            runId,
            config,
            LocalDateTime.now(),
            LocalDateTime.now(),
            BigDecimal.valueOf(avgPrecisionAt5),
            BigDecimal.valueOf(avgMrr),
            null
        );
    }
    
    private EvalMetrics evaluateCase(Long runId, EvalCase evalCase) {
        // Retrieve chunks for the question
        List<RetrievedChunk> retrieved = retriever.retrieve(evalCase.question(), 5);
        Long[] retrievedIds = retrieved.stream()
            .map(rc -> rc.chunk().id())
            .toArray(Long[]::new);
        
        // Calculate precision@5
        Set<Long> expected = new HashSet<>(Arrays.asList(evalCase.expectedChunkIds()));
        Set<Long> actual = new HashSet<>(Arrays.asList(retrievedIds));
        
        long relevantRetrieved = actual.stream()
            .filter(expected::contains)
            .count();
        
        double precision = (double) relevantRetrieved / Math.min(5, retrievedIds.length);
        
        // Calculate reciprocal rank
        double reciprocalRank = 0.0;
        for (int i = 0; i < retrievedIds.length; i++) {
            if (expected.contains(retrievedIds[i])) {
                reciprocalRank = 1.0 / (i + 1);
                break;
            }
        }
        
        // Save result
        jdbcTemplate.update(
            """
            INSERT INTO eval_result 
            (run_id, case_id, retrieved_chunk_ids, precision, reciprocal_rank, created_at)
            VALUES (?, ?, ?, ?, ?, NOW())
            """,
            runId,
            evalCase.id(),
            retrievedIds,
            BigDecimal.valueOf(precision).setScale(4, RoundingMode.HALF_UP),
            BigDecimal.valueOf(reciprocalRank).setScale(4, RoundingMode.HALF_UP)
        );
        
        return new EvalMetrics(precision, reciprocalRank);
    }
    
    private List<EvalCase> loadEvalCases() {
        return jdbcTemplate.query(
            "SELECT * FROM eval_case ORDER BY id",
            (rs, rowNum) -> new EvalCase(
                rs.getLong("id"),
                rs.getString("category"),
                rs.getString("question"),
                rs.getString("expected_answer"),
                (Long[]) rs.getArray("expected_chunk_ids").getArray(),
                rs.getString("source_filter"),
                rs.getTimestamp("created_at").toLocalDateTime()
            )
        );
    }
    
    private record EvalMetrics(double precisionAt5, double mrr) {}
}

