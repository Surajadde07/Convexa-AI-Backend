package com.convexa.ai.convexa_ai_backend.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagedMembersResponse {
    private List<EmployeeResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
