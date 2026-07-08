package com.example.person.service;

import com.example.person.domain.Address;
import com.example.person.domain.Person;
import com.example.person.domain.PhoneNumber;
import com.example.person.service.dto.AddressInput;
import com.example.person.service.dto.AddressView;
import com.example.person.service.dto.CreatePersonInput;
import com.example.person.service.dto.PersonView;
import com.example.person.service.dto.PhoneNumberView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/**
 * Maps between the persistence model and the service DTOs: entity to {@link PersonView}
 * (enriched with the values {@link PersonCalculations} derives) and
 * {@link CreatePersonInput} to a new entity. Must be invoked inside a transaction so
 * lazy collections can be materialized.
 */
@Component
public class PersonMapper {

    private static final Logger log = LoggerFactory.getLogger(PersonMapper.class);

    private final PersonCalculations calculations;

    public PersonMapper(PersonCalculations calculations) {
        this.calculations = calculations;
    }

    public PersonView toView(Person person) {
        PersonView view = new PersonView();
        view.setId(person.getId());
        view.setFirstName(person.getFirstName());
        view.setLastName(person.getLastName());
        view.setFullName(person.getFirstName() + " " + person.getLastName());
        view.setEmail(person.getEmail());
        view.setBirthDate(person.getBirthDate());
        view.setAge(calculations.age(person.getBirthDate()));
        view.setGender(person.getGender());
        view.setSalary(person.getSalary());
        view.setMonthlyNetSalary(calculations.monthlyNetSalary(person.getSalary()));
        view.setActive(person.isActive());
        view.setHireDate(person.getHireDate());
        view.setYearsOfService(calculations.yearsOfService(person.getHireDate()));
        view.setHeightCm(person.getHeightCm());
        view.setWeightKg(person.getWeightKg());
        view.setBmi(calculations.bmi(person.getHeightCm(), person.getWeightKg()));
        view.setAddress(toAddressView(person.getAddress()));
        // copy lazy collections while the session is still open
        view.setPhoneNumbers(person.getPhoneNumbers().stream()
                .map(phone -> new PhoneNumberView(phone.getType(), phone.getNumber()))
                .collect(Collectors.toList()));
        view.setHobbies(new LinkedHashSet<>(person.getHobbies()));
        view.setCreatedAt(person.getCreatedAt());
        log.debug("Computed view for person {}: age={}, yearsOfService={}, monthlyNetSalary={}, bmi={}",
                view.getId(), view.getAge(), view.getYearsOfService(), view.getMonthlyNetSalary(), view.getBmi());
        return view;
    }

    public Person toEntity(CreatePersonInput input) {
        Person person = new Person(input.getFirstName(), input.getLastName(), input.getEmail());
        person.setBirthDate(input.getBirthDate());
        person.setGender(input.getGender());
        person.setSalary(input.getSalary());
        person.setActive(input.getActive() == null || input.getActive());
        person.setHireDate(input.getHireDate());
        person.setHeightCm(input.getHeightCm());
        person.setWeightKg(input.getWeightKg());
        person.setHobbies(input.getHobbies());
        person.setAddress(toAddress(input.getAddress()));
        if (input.getPhoneNumbers() != null) {
            input.getPhoneNumbers().forEach(phone ->
                    person.addPhoneNumber(new PhoneNumber(phone.getType(), phone.getNumber())));
        }
        return person;
    }

    private AddressView toAddressView(Address address) {
        if (address == null) {
            return null;
        }
        return new AddressView(
                address.getStreet(),
                address.getHouseNumber(),
                address.getCity(),
                address.getZipCode(),
                address.getCountry());
    }

    private Address toAddress(AddressInput input) {
        if (input == null) {
            return null;
        }
        return new Address(
                input.getStreet(),
                input.getHouseNumber(),
                input.getCity(),
                input.getZipCode(),
                input.getCountry());
    }
}
