package com.example.person.service;

import com.example.infrastructure.exception.EntityNotFoundException;
import com.example.person.dal.PersonDal;
import com.example.person.domain.Address;
import com.example.person.domain.Person;
import com.example.person.domain.PhoneNumber;
import com.example.person.service.dto.AddressView;
import com.example.person.service.dto.CreatePersonInput;
import com.example.person.service.dto.PersonView;
import com.example.person.service.dto.PhoneNumberView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Business layer of the person domain. Talks to the DAL for persistence and enriches raw
 * entities with calculated values (full name, age, years of service, net salary, BMI)
 * before handing {@link PersonView}s up to the GraphQL layer.
 */
@Service
public class PersonService {

    private static final BigDecimal INCOME_TAX_RATE = new BigDecimal("0.25");
    private static final int MONTHS_PER_YEAR = 12;

    private final PersonDal personDal;

    public PersonService(PersonDal personDal) {
        this.personDal = personDal;
    }

    @Transactional(readOnly = true)
    public PersonView getPerson(long id) {
        Person person = personDal.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("Person", id));
        return toView(person);
    }

    @Transactional(readOnly = true)
    public List<PersonView> getAllPersons() {
        return personDal.findAll().stream().map(this::toView).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PersonView> getPersonsByCity(String city) {
        return personDal.findByCity(city).stream().map(this::toView).collect(Collectors.toList());
    }

    @Transactional
    public PersonView createPerson(CreatePersonInput input) {
        if (personDal.emailExists(input.getEmail())) {
            throw new IllegalArgumentException("A person with email " + input.getEmail() + " already exists");
        }

        Person person = new Person(input.getFirstName(), input.getLastName(), input.getEmail());
        person.setBirthDate(input.getBirthDate());
        person.setGender(input.getGender());
        person.setSalary(input.getSalary());
        person.setActive(input.getActive() == null || input.getActive());
        person.setHireDate(input.getHireDate());
        person.setHeightCm(input.getHeightCm());
        person.setWeightKg(input.getWeightKg());
        person.setHobbies(input.getHobbies());

        if (input.getAddress() != null) {
            person.setAddress(new Address(
                    input.getAddress().getStreet(),
                    input.getAddress().getHouseNumber(),
                    input.getAddress().getCity(),
                    input.getAddress().getZipCode(),
                    input.getAddress().getCountry()));
        }
        if (input.getPhoneNumbers() != null) {
            input.getPhoneNumbers().forEach(phone ->
                    person.addPhoneNumber(new PhoneNumber(phone.getType(), phone.getNumber())));
        }

        return toView(personDal.save(person));
    }

    @Transactional
    public PersonView updateSalary(long id, BigDecimal newSalary) {
        Person person = personDal.findById(id)
                .orElseThrow(() -> EntityNotFoundException.of("Person", id));
        person.setSalary(newSalary);
        return toView(personDal.save(person));
    }

    @Transactional
    public boolean deletePerson(long id) {
        if (!personDal.exists(id)) {
            return false;
        }
        personDal.deleteById(id);
        return true;
    }

    // ------------------------------------------------------------------
    // Calculations - the "business logic" this layer adds on top of raw data
    // ------------------------------------------------------------------

    Integer calculateAge(LocalDate birthDate) {
        return birthDate == null ? null : Period.between(birthDate, LocalDate.now()).getYears();
    }

    Integer calculateYearsOfService(LocalDate hireDate) {
        return hireDate == null ? null : Period.between(hireDate, LocalDate.now()).getYears();
    }

    /** Monthly salary after flat income tax, from the annual gross salary. */
    BigDecimal calculateMonthlyNetSalary(BigDecimal annualGrossSalary) {
        if (annualGrossSalary == null) {
            return null;
        }
        return annualGrossSalary
                .multiply(BigDecimal.ONE.subtract(INCOME_TAX_RATE))
                .divide(BigDecimal.valueOf(MONTHS_PER_YEAR), 2, RoundingMode.HALF_UP);
    }

    Double calculateBmi(Integer heightCm, Double weightKg) {
        if (heightCm == null || weightKg == null || heightCm <= 0) {
            return null;
        }
        double heightMeters = heightCm / 100.0;
        double bmi = weightKg / (heightMeters * heightMeters);
        return Math.round(bmi * 10.0) / 10.0;
    }

    // ------------------------------------------------------------------
    // Entity -> view mapping (runs inside the transaction so lazy collections load)
    // ------------------------------------------------------------------

    private PersonView toView(Person person) {
        PersonView view = new PersonView();
        view.setId(person.getId());
        view.setFirstName(person.getFirstName());
        view.setLastName(person.getLastName());
        view.setFullName(person.getFirstName() + " " + person.getLastName());
        view.setEmail(person.getEmail());
        view.setBirthDate(person.getBirthDate());
        view.setAge(calculateAge(person.getBirthDate()));
        view.setGender(person.getGender());
        view.setSalary(person.getSalary());
        view.setMonthlyNetSalary(calculateMonthlyNetSalary(person.getSalary()));
        view.setActive(person.isActive());
        view.setHireDate(person.getHireDate());
        view.setYearsOfService(calculateYearsOfService(person.getHireDate()));
        view.setHeightCm(person.getHeightCm());
        view.setWeightKg(person.getWeightKg());
        view.setBmi(calculateBmi(person.getHeightCm(), person.getWeightKg()));
        // copy lazy collections while the session is still open
        view.setHobbies(new LinkedHashSet<>(person.getHobbies()));
        view.setCreatedAt(person.getCreatedAt());

        if (person.getAddress() != null) {
            Address address = person.getAddress();
            view.setAddress(new AddressView(
                    address.getStreet(),
                    address.getHouseNumber(),
                    address.getCity(),
                    address.getZipCode(),
                    address.getCountry()));
        }
        view.setPhoneNumbers(person.getPhoneNumbers().stream()
                .map(phone -> new PhoneNumberView(phone.getType(), phone.getNumber()))
                .collect(Collectors.toList()));
        return view;
    }
}
