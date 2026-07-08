package com.example.person.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

/**
 * Pure business calculations of the person domain. No I/O, no state - trivially
 * unit-testable, reusable from any service or mapper.
 */
@Component
public class PersonCalculations {

    private static final BigDecimal INCOME_TAX_RATE = new BigDecimal("0.25");
    private static final int MONTHS_PER_YEAR = 12;
    private static final int MONEY_SCALE = 2;
    private static final double CM_PER_METER = 100.0;

    public Integer age(LocalDate birthDate) {
        return wholeYearsSince(birthDate);
    }

    public Integer yearsOfService(LocalDate hireDate) {
        return wholeYearsSince(hireDate);
    }

    /** Monthly salary after flat income tax, from the annual gross salary. */
    public BigDecimal monthlyNetSalary(BigDecimal annualGrossSalary) {
        if (annualGrossSalary == null) {
            return null;
        }
        return annualGrossSalary
                .multiply(BigDecimal.ONE.subtract(INCOME_TAX_RATE))
                .divide(BigDecimal.valueOf(MONTHS_PER_YEAR), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Body mass index rounded to one decimal, or null when height/weight are unknown. */
    public Double bmi(Integer heightCm, Double weightKg) {
        if (heightCm == null || weightKg == null || heightCm <= 0) {
            return null;
        }
        double heightMeters = heightCm / CM_PER_METER;
        double bmi = weightKg / (heightMeters * heightMeters);
        return Math.round(bmi * 10.0) / 10.0;
    }

    private Integer wholeYearsSince(LocalDate date) {
        return date == null ? null : Period.between(date, LocalDate.now()).getYears();
    }
}
