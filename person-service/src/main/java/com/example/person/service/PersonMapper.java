package com.example.person.service;

import com.example.infrastructure.mapping.DeclarativeMapper;
import com.example.person.domain.Person;
import com.example.person.service.dto.CreatePersonInput;
import com.example.person.service.dto.PersonView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maps between the persistence model and the service DTOs. All same-named fields
 * (including nested address, phone numbers and hobbies) flow through the
 * {@link DeclarativeMapper} with zero code - adding a simple field to entity + view +
 * schema requires no change here. Only the values that genuinely differ from storage
 * are set explicitly: the ones {@link PersonCalculations} derives.
 *
 * <p>Must be invoked inside a transaction so lazy collections can be materialized.
 */
@Component
public class PersonMapper {

    private static final Logger log = LoggerFactory.getLogger(PersonMapper.class);

    private final PersonCalculations calculations;
    private final DeclarativeMapper declarativeMapper;

    public PersonMapper(PersonCalculations calculations, DeclarativeMapper declarativeMapper) {
        this.calculations = calculations;
        this.declarativeMapper = declarativeMapper;
    }

    public PersonView toView(Person person) {
        PersonView view = declarativeMapper.map(person, PersonView.class);
        view.setFullName(person.getFirstName() + " " + person.getLastName());
        view.setAge(calculations.age(person.getBirthDate()));
        view.setMonthlyNetSalary(calculations.monthlyNetSalary(person.getSalary()));
        view.setYearsOfService(calculations.yearsOfService(person.getHireDate()));
        view.setBmi(calculations.bmi(person.getHeightCm(), person.getWeightKg()));
        log.debug("Computed view for person {}: age={}, yearsOfService={}, monthlyNetSalary={}, bmi={}",
                view.getId(), view.getAge(), view.getYearsOfService(), view.getMonthlyNetSalary(), view.getBmi());
        return view;
    }

    /**
     * Field-by-field copying is delegated to the {@link DeclarativeMapper}; how nested
     * parts are wired is declared on the entity itself ({@code @JsonManagedReference} /
     * {@code @JsonBackReference} connect each phone number back to its person).
     */
    public Person toEntity(CreatePersonInput input) {
        return declarativeMapper.map(input, Person.class);
    }
}
