# Quarantined Tests

These test classes exist on disk but are untracked in git. They encode expectations for features that do not exist on the tracked tree (phantom phases from prior uncommitted work). They are quarantined to prevent the reactor from failing on contracts that were never implemented.

## AnalyticsCacheInvalidationTest
- **File**: `AnalyticsCacheInvalidationTest.java`
- **Contract gaps encoded**:
  - `PersistenceService.countBySource(source)` — method does not exist
  - `totalRecords` field on stats response — field does not exist
  - Cache eviction semantics on ingest — not wired
- **TODO**: Rebuild as part of the analytics-decomposition phase (DB-backed analytics with proper cache contract)

## CrossFormatCanonicalityTest
- **File**: `CrossFormatCanonicalityTest.java`
- **Contract gaps encoded**:
  - CSV ingest path — blocked by commons-io dependency defect (see defect ticket)
  - Cross-format canonicality guarantee — normalization pipeline not implemented
- **TODO**: Rebuild as part of the validation-normalization phase

## MalformedInputH2Test
- **File**: `MalformedInputH2Test.java`
- **Contract gaps encoded**:
  - CSV ingest path — blocked by commons-io dependency defect
  - `errors` array in validation error response — `GlobalExceptionHandler` does not add this key
  - Uniform error envelope for all 400s — not fully implemented
- **TODO**: Rebuild as part of the validation-normalization phase + error-envelope completion
