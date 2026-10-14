package netdiag.diagnostic;

import java.util.Objects;

/**
 * Представляет один диагностический блок из формата задания.
 */
public final class DiagnosticResult {
    private final String stage;
    private final String result;
    private final String classification;
    private final String fact;
    private final String next;

    public DiagnosticResult(
            String stage,
            String result,
            String classification,
            String fact,
            String next
    ) {
        this.stage = Objects.requireNonNull(stage, "stage must not be null");
        this.result = Objects.requireNonNull(result, "result must not be null");
        this.classification = Objects.requireNonNull(
                classification,
                "classification must not be null"
        );
        this.fact = Objects.requireNonNull(fact, "fact must not be null");
        this.next = Objects.requireNonNull(next, "next must not be null");
    }

    public String getStage() {
        return stage;
    }

    public String getResult() {
        return result;
    }

    public String getClassification() {
        return classification;
    }

    public String getFact() {
        return fact;
    }

    public String getNext() {
        return next;
    }
}
