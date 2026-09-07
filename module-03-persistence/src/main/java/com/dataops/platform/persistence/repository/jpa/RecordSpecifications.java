package com.dataops.platform.persistence.repository.jpa;

import com.dataops.platform.persistence.entity.PersistedRecord;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA {@link Specification} builder for {@link PersistedRecord} query filters.
 *
 * <p>Each filter is conditional: when the caller omits it (null), no predicate
 * is added. This is what makes the single {@code findRecords(spec, pageable)}
 * entry point usable for "all filters optional" pagination without a combinatorial
 * explosion of repository methods.
 *
 * <p>Range bounds are inclusive on both ends: {@code ingestedAt >= from AND
 * ingestedAt <= to}. This matches the documented API contract.
 *
 * <p>The API exposes {@link Instant} (an absolute UTC moment) but the entity
 * column is {@code LocalDateTime} stored in UTC. Conversion happens here at the
 * persistence boundary; callers never see {@code LocalDateTime}.
 */
public final class RecordSpecifications {

    private RecordSpecifications() {
    }

    public static Specification<PersistedRecord> withFilters(
            String source,
            String type,
            Instant from,
            Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>(4);
            if (source != null && !source.isBlank()) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (type != null && !type.isBlank()) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (from != null) {
                LocalDateTime fromUtc = LocalDateTime.ofInstant(from, ZoneOffset.UTC);
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("ingestedAt"), fromUtc));
            }
            if (to != null) {
                LocalDateTime toUtc = LocalDateTime.ofInstant(to, ZoneOffset.UTC);
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDateTime>get("ingestedAt"), toUtc));
            }
            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
