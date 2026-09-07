package com.dataops.platform.common.exception;

/**
 * Single, machine-parseable issue produced by the validation pipeline.
 *
 * <p>{@code row} is omitted (-1) for single-record endpoints (JSON, XML, file
 * upload of one record) and set to the 1-based row index for batched inputs
 * (CSV, file upload of many records). {@code field} is a dot-notation path
 * into the canonical payload shape, so producers can locate the exact field
 * that failed.
 */
public record ValidationIssue(int row, String field, String message) {

    public static final int SINGLE_RECORD_ROW = -1;

    public static ValidationIssue of(String field, String message) {
        return new ValidationIssue(SINGLE_RECORD_ROW, field, message);
    }

    public static ValidationIssue of(int row, String field, String message) {
        return new ValidationIssue(row, field, message);
    }
}
