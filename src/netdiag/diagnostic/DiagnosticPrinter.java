package netdiag.diagnostic;

/**
 * Печатает диагностический результат в требуемом заданием формате.
 */
public final class DiagnosticPrinter {
    private DiagnosticPrinter() {
    }

    public static void print(DiagnosticResult diagnostic) {
        if (diagnostic == null) {
            throw new IllegalArgumentException("Diagnostic result must not be null");
        }

        System.out.printf("STAGE: %s%n", diagnostic.getStage());
        System.out.printf("RESULT: %s%n", diagnostic.getResult());
        System.out.printf("CLASS: %s%n", diagnostic.getClassification());
        System.out.printf("FACT: %s%n", diagnostic.getFact());
        System.out.printf("NEXT: %s%n", diagnostic.getNext());
    }
}
