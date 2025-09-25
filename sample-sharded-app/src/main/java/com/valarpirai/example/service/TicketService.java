package com.valarpirai.example.service;

import com.valarpirai.example.dto.TicketCreateRequest;
import com.valarpirai.example.dto.TicketUpdateRequest;
import com.valarpirai.example.entity.Ticket;
import com.valarpirai.example.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class TicketService {

    private static final Logger logger = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;

    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    public List<Ticket> getAllActiveTickets(Long tenantId, Long statusId, String category, Long requesterId, Long responderId) {
        if (statusId != null || StringUtils.hasText(category) || requesterId != null || responderId != null) {
            return ticketRepository.findFilteredTickets(tenantId, statusId, category, requesterId, responderId);
        }
        return ticketRepository.findByAccountIdAndDeletedFalse(tenantId);
    }

    public Ticket getTicketById(Long id, Long tenantId) {
        return ticketRepository.findByIdAndAccountIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Ticket not found: " + id));
    }

    @Transactional
    public Ticket createTicket(TicketCreateRequest request, Long tenantId) {
        Ticket ticket = Ticket.builder()
                .accountId(tenantId)
                .subject(request.getSubject())
                .description(request.getDescription())
                .requesterId(request.getRequesterId())
                .responderId(request.getResponderId())
                .statusId(request.getStatusId())
                .category(request.getCategory())
                .subcategory(request.getSubcategory())
                .priority(request.getPriority())
                .build();

        ticket = ticketRepository.save(ticket);
        logger.info("Created ticket: {} for tenant: {}", ticket.getId(), tenantId);
        return ticket;
    }

    @Transactional
    public Ticket updateTicket(Long id, TicketUpdateRequest request, Long tenantId) {
        Ticket ticket = getTicketById(id, tenantId);

        if (StringUtils.hasText(request.getSubject())) {
            ticket.setSubject(request.getSubject());
        }
        if (request.getDescription() != null) {
            ticket.setDescription(request.getDescription());
        }
        if (request.getRequesterId() != null) {
            ticket.setRequesterId(request.getRequesterId());
        }
        if (request.getResponderId() != null) {
            ticket.setResponderId(request.getResponderId());
        }
        if (request.getStatusId() != null) {
            ticket.setStatusId(request.getStatusId());
        }
        if (StringUtils.hasText(request.getCategory())) {
            ticket.setCategory(request.getCategory());
        }
        if (StringUtils.hasText(request.getSubcategory())) {
            ticket.setSubcategory(request.getSubcategory());
        }
        if (request.getPriority() != null) {
            ticket.setPriority(request.getPriority());
        }

        ticket = ticketRepository.save(ticket);
        logger.info("Updated ticket: {} for tenant: {}", ticket.getId(), tenantId);
        return ticket;
    }

    @Transactional
    public void deleteTicket(Long id, Long tenantId) {
        Ticket ticket = getTicketById(id, tenantId);
        ticket.delete();
        ticketRepository.save(ticket);
        logger.info("Deleted ticket: {} for tenant: {}", id, tenantId);
    }

    @Transactional
    public Ticket assignTicket(Long ticketId, Long responderId, Long tenantId) {
        Ticket ticket = getTicketById(ticketId, tenantId);
        ticket.setResponderId(responderId);
        ticket = ticketRepository.save(ticket);
        logger.info("Assigned ticket: {} to responder: {} for tenant: {}", ticketId, responderId, tenantId);
        return ticket;
    }
}