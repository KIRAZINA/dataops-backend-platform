package com.dataops.platform.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic paginated response wrapper for list endpoints.
 * Supports pagination metadata along with the result data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagedResponse<T> {
    
    /**
     * The paginated content items
     */
    private List<T> content;
    
    /**
     * Current page number (0-indexed)
     */
    @JsonProperty("page_number")
    private int pageNumber;
    
    /**
     * Page size
     */
    @JsonProperty("page_size")
    private int pageSize;
    
    /**
     * Total number of elements across all pages
     */
    @JsonProperty("total_elements")
    private long totalElements;
    
    /**
     * Total number of pages
     */
    @JsonProperty("total_pages")
    private int totalPages;
    
    /**
     * Is this the last page
     */
    @JsonProperty("is_last")
    private boolean isLast;
    
    /**
     * Is this the first page
     */
    @JsonProperty("is_first")
    private boolean isFirst;

    /**
     * Factory method to create a PagedResponse with calculated metadata
     */
    public static <T> PagedResponse<T> of(List<T> content, int pageNumber, int pageSize, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        int zeroIndexedPageNumber = Math.max(0, pageNumber);
        return PagedResponse.<T>builder()
                .content(content)
                .pageNumber(zeroIndexedPageNumber)
                .pageSize(pageSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .isFirst(zeroIndexedPageNumber == 0)
                .isLast(zeroIndexedPageNumber >= totalPages - 1)
                .build();
    }
}
