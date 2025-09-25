package com.valarpirai.example.dto;

import com.valarpirai.example.entity.Priority;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TicketResponse {
    private Long id;
    private Long accountId;
    private String subject;
    private String description;
    private Long requesterId;
    private Long responderId;
    private Long statusId;
    private String category;
    private String subcategory;
    private Priority priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}