package com.valarpirai.example.dto;

import com.valarpirai.example.entity.global.Priority;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
public class TicketCreateRequest {

    @NotBlank(message = "Subject is required")
    @Size(max = 500, message = "Subject must not exceed 500 characters")
    private String subject;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    @NotNull(message = "Requester ID is required")
    private Long requesterId;

    private Long responderId;

    @NotNull(message = "Status ID is required")
    private Long statusId;

    @Size(max = 100, message = "Category must not exceed 100 characters")
    private String category;

    @Size(max = 100, message = "Subcategory must not exceed 100 characters")
    private String subcategory;

    private Priority priority = Priority.MEDIUM;
}