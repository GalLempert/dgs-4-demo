package com.example.person.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit tests - no Spring context needed for pure calculations. */
class PersonCalculationsTest {

    private final PersonCalculations calculations = new PersonCalculations();

    @Test
    void ageIsWholeYearsSinceBirthDate() {
        assertThat(calculations.age(LocalDate.now().minusYears(30).minusDays(1))).isEqualTo(30);
        assertThat(calculations.age(null)).isNull();
    }

    @Test
    void yearsOfServiceIsWholeYearsSinceHireDate() {
        assertThat(calculations.yearsOfService(LocalDate.now().minusYears(5).minusDays(1))).isEqualTo(5);
        assertThat(calculations.yearsOfService(null)).isNull();
    }

    @Test
    void monthlyNetSalaryAppliesFlatTaxAndDividesByTwelve() {
        assertThat(calculations.monthlyNetSalary(new BigDecimal("720000")))
                .isEqualByComparingTo("45000.00");
        assertThat(calculations.monthlyNetSalary(null)).isNull();
    }

    @Test
    void bmiIsRoundedToOneDecimal() {
        assertThat(calculations.bmi(177, 75.0)).isEqualTo(23.9);
        assertThat(calculations.bmi(null, 75.0)).isNull();
        assertThat(calculations.bmi(177, null)).isNull();
        assertThat(calculations.bmi(0, 75.0)).isNull();
    }
}
