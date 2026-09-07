package com.dataops.platform.common.exception;

import java.util.Collections;
import java.util.List;

/**
 * Thrown by the validation/ingestion pipeline when one or more records fail
 * structural or business validation. Carries the full list of issues so the
 * producer gets actionable feedback in a single round-trip rather than
 * resubmitting one error at a time.
 */
public class RecordValidationException extends RuntimeException {

    private final List<ValidationIssue> issues;
    private final int totalRecords;

    public RecordValidationException(List<ValidationIssue> issues) {
        this(issues, 0);
    }

    public RecordValidationException(List<ValidationIssue> issues, int totalRecords) {
        super(buildMessage(issues, totalRecords));
        this.issues = issues == null ? List.of() : List.copyOf(issues);
        this.totalRecords = totalRecords;
    }

    public RecordValidationException(String message) {
        this(List.of(new ValidationIssue(ValidationIssue.SINGLE_RECORD_ROW, null, message)));
    }

    public List<ValidationIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    private static String buildMessage(List<ValidationIssue> issues, int totalRecords) {
        if (issues == null || issues.isEmpty()) {
            return "Ingestion rejected: validation failed";
        }
        if (totalRecords > 0) {
            return "Ingestion rejected: " + issues.size() + " of " + totalRecords + " records failed validation";
        }
        return "Ingestion rejected: " + issues.size() + " issue(s)";
    }
}
