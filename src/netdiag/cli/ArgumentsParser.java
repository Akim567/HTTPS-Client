package netdiag.cli;

import java.nio.file.Path;

/**
 * Отвечает только за аргументы командной строки.
 */
public final class ArgumentsParser {
    private static final int DEFAULT_TIMEOUT_MILLIS = 5_000;

    private ArgumentsParser() {
    }

    public static CommandLineArguments parse(String[] args) {
        if (args == null) {
            throw new IllegalArgumentException("Arguments must not be null");
        }

        String url = null;
        Path caCertificatePath = null;
        int timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
        boolean caWasSpecified = false;
        boolean timeoutWasSpecified = false;

        for (int index = 0; index < args.length; index++) {
            String argument = args[index];

            switch (argument) {
                case "--ca" -> {
                    if (caWasSpecified) {
                        throw new IllegalArgumentException("--ca may only be specified once");
                    }

                    String value = requireOptionValue(args, index, "--ca");
                    if (value.isBlank()) {
                        throw new IllegalArgumentException("--ca path must not be empty");
                    }

                    caCertificatePath = Path.of(value);
                    caWasSpecified = true;
                    index++;
                }
                case "--timeout" -> {
                    if (timeoutWasSpecified) {
                        throw new IllegalArgumentException(
                                "--timeout may only be specified once"
                        );
                    }

                    String value = requireOptionValue(args, index, "--timeout");
                    timeoutMillis = parseTimeout(value);
                    timeoutWasSpecified = true;
                    index++;
                }
                default -> {
                    if (argument.startsWith("--")) {
                        throw new IllegalArgumentException("Unknown option: " + argument);
                    }
                    if (url != null) {
                        throw new IllegalArgumentException(
                                "Only one URL may be specified"
                        );
                    }
                    if (argument.isBlank()) {
                        throw new IllegalArgumentException("URL must not be empty");
                    }

                    url = argument;
                }
            }
        }

        if (url == null) {
            throw new IllegalArgumentException("URL is required");
        }

        return new CommandLineArguments(url, caCertificatePath, timeoutMillis);
    }

    public static String usage() {
        return "Usage: netdiag <url> [--ca <certificate-path>] "
                + "[--timeout <milliseconds>]";
    }

    private static String requireOptionValue(
            String[] args,
            int optionIndex,
            String optionName
    ) {
        int valueIndex = optionIndex + 1;
        if (valueIndex >= args.length || args[valueIndex].startsWith("--")) {
            throw new IllegalArgumentException(optionName + " requires a value");
        }

        return args[valueIndex];
    }

    private static int parseTimeout(String value) {
        int timeoutMillis;
        try {
            timeoutMillis = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "--timeout must be a whole number of milliseconds",
                    exception
            );
        }

        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("--timeout must be greater than zero");
        }

        return timeoutMillis;
    }
}
