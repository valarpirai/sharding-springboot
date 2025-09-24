package com.example.demo.controller;

import com.example.demo.entity.Customer;
import com.example.demo.service.CustomerService;
import com.valarpirai.sharding.context.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for customer operations.
 * Demonstrates multi-tenant operations with sharding.
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    @Autowired
    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /**
     * Create a new customer.
     * Tenant context must be set before calling this endpoint.
     */
    @PostMapping
    public ResponseEntity<Customer> createCustomer(@RequestBody Customer customer) {
        if (!TenantContext.isContextSet()) {
            return ResponseEntity.badRequest().build();
        }

        // Set tenant ID from context
        customer.setTenantId(TenantContext.getCurrentTenantId());
        Customer savedCustomer = customerService.createCustomer(customer);
        return ResponseEntity.ok(savedCustomer);
    }

    /**
     * Get all customers for the current tenant.
     */
    @GetMapping
    public ResponseEntity<List<Customer>> getCustomers() {
        if (!TenantContext.isContextSet()) {
            return ResponseEntity.badRequest().build();
        }

        List<Customer> customers = customerService.getCustomersByTenant(TenantContext.getCurrentTenantId());
        return ResponseEntity.ok(customers);
    }

    /**
     * Get a specific customer by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomer(@PathVariable Long id) {
        if (!TenantContext.isContextSet()) {
            return ResponseEntity.badRequest().build();
        }

        return customerService.getCustomer(id, TenantContext.getCurrentTenantId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update a customer.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Customer> updateCustomer(@PathVariable Long id, @RequestBody Customer customer) {
        if (!TenantContext.isContextSet()) {
            return ResponseEntity.badRequest().build();
        }

        customer.setTenantId(TenantContext.getCurrentTenantId());
        return customerService.updateCustomer(id, customer)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a customer.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        if (!TenantContext.isContextSet()) {
            return ResponseEntity.badRequest().build();
        }

        boolean deleted = customerService.deleteCustomer(id, TenantContext.getCurrentTenantId());
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /**
     * Set tenant context for demonstration purposes.
     * In a real application, this would be done via JWT tokens or headers.
     */
    @PostMapping("/set-tenant/{tenantId}")
    public ResponseEntity<String> setTenant(@PathVariable Long tenantId) {
        TenantContext.setTenantId(tenantId);
        return ResponseEntity.ok("Tenant context set to: " + tenantId);
    }

    /**
     * Clear tenant context.
     */
    @PostMapping("/clear-tenant")
    public ResponseEntity<String> clearTenant() {
        TenantContext.clear();
        return ResponseEntity.ok("Tenant context cleared");
    }
}