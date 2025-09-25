package com.valarpirai.example.dto;

import com.valarpirai.example.entity.Priority;
import lombok.Data;

import javax.validation.constraints.Size;

@Data
public class TicketUpdateRequest {

    @Size(max = 500, message = "Subject must not exceed 500 characters")
    private String subject;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    private Long requesterId;

    private Long responderId;

    private Long statusId;

    @Size(max = 100, message = "Category must not exceed 100 characters")
    private String category;

    @Size(max = 100, message = "Subcategory must not exceed 100 characters")
    private String subcategory;

    private Priority priority;
}