package com.example.demo.service;

import com.example.demo.entity.Customer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service layer for customer operations.
 * This is a simple in-memory implementation for demonstration.
 * In a real application, this would use JPA repositories.
 */
@Service
public class CustomerService {

    // Simulate database with in-memory storage
    private final Map<Long, Customer> customers = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public Customer createCustomer(Customer customer) {
        customer.setId(idGenerator.getAndIncrement());
        customers.put(customer.getId(), customer);
        return customer;
    }

    public List<Customer> getCustomersByTenant(Long tenantId) {
        return customers.values().stream()
                .filter(customer -> customer.getTenantId().equals(tenantId))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public Optional<Customer> getCustomer(Long id, Long tenantId) {
        Customer customer = customers.get(id);
        if (customer != null && customer.getTenantId().equals(tenantId)) {
            return Optional.of(customer);
        }
        return Optional.empty();
    }

    public Optional<Customer> updateCustomer(Long id, Customer updatedCustomer) {
        Customer existing = customers.get(id);
        if (existing != null && existing.getTenantId().equals(updatedCustomer.getTenantId())) {
            updatedCustomer.setId(id);
            customers.put(id, updatedCustomer);
            return Optional.of(updatedCustomer);
        }
        return Optional.empty();
    }

    public boolean deleteCustomer(Long id, Long tenantId) {
        Customer customer = customers.get(id);
        if (customer != null && customer.getTenantId().equals(tenantId)) {
            customers.remove(id);
            return true;
        }
        return false;
    }
}