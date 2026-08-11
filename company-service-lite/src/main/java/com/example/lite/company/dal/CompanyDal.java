package com.example.lite.company.dal;

import com.example.lite.company.domain.Company;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/** Trivial in-memory data access layer - stands in for whatever DAL already exists. */
@Component
public class CompanyDal {

    private final Map<Long, Company> companiesById = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong();

    public List<Company> findAll() {
        return companiesById.values().stream()
                .sorted(Comparator.comparing(Company::getId))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public Optional<Company> findById(long id) {
        return Optional.ofNullable(companiesById.get(id));
    }

    public Company save(Company company) {
        if (company.getId() == null) {
            company.setId(idSequence.incrementAndGet());
        }
        companiesById.put(company.getId(), company);
        return company;
    }
}
