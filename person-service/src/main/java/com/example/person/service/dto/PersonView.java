package com.example.person.service.dto;

import com.example.infrastructure.graphql.model.GraphQLEnum;
import com.example.infrastructure.graphql.model.GraphQLModel;
import com.example.infrastructure.graphql.model.GraphQLTemporal;
import com.example.infrastructure.graphql.model.ResourceView;
import com.example.person.domain.Gender;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * What the GraphQL layer exposes for a person: all stored fields plus the values the
 * service layer calculates (fullName, age, yearsOfService, monthlyNetSalary, bmi).
 * The technical fields (id, version, createdAt, updatedAt, sequence, deleted) are
 * inherited from {@link ResourceView} - the schema mirrors this with
 * {@code Person implements Resource}.
 *
 * <p>Presentation annotations declare which fields are serialized as more than their
 * raw value: {@code @GraphQLTemporal} fields take a {@code format} argument
 * (ISO/UNIX/RFC_1123), {@code @GraphQLEnum} fields are enriched from the enum catalog
 * into {@code EnumValue} objects. Unannotated fields return their plain value.
 */
@GraphQLModel("Person")
public class PersonView extends ResourceView {

    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String nickname;

    @GraphQLTemporal
    private LocalDate birthDate;

    private Integer age;

    @GraphQLEnum("gender")
    private Gender gender;

    private BigDecimal salary;
    private BigDecimal monthlyNetSalary;
    private boolean active;

    @GraphQLTemporal
    private LocalDate hireDate;

    private Integer yearsOfService;
    private Integer heightCm;
    private Double weightKg;
    private Double bmi;
    private AddressView address;
    private List<PhoneNumberView> phoneNumbers;
    private Set<String> hobbies;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public BigDecimal getSalary() {
        return salary;
    }

    public void setSalary(BigDecimal salary) {
        this.salary = salary;
    }

    public BigDecimal getMonthlyNetSalary() {
        return monthlyNetSalary;
    }

    public void setMonthlyNetSalary(BigDecimal monthlyNetSalary) {
        this.monthlyNetSalary = monthlyNetSalary;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public Integer getYearsOfService() {
        return yearsOfService;
    }

    public void setYearsOfService(Integer yearsOfService) {
        this.yearsOfService = yearsOfService;
    }

    public Integer getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(Integer heightCm) {
        this.heightCm = heightCm;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    public Double getBmi() {
        return bmi;
    }

    public void setBmi(Double bmi) {
        this.bmi = bmi;
    }

    public AddressView getAddress() {
        return address;
    }

    public void setAddress(AddressView address) {
        this.address = address;
    }

    public List<PhoneNumberView> getPhoneNumbers() {
        return phoneNumbers;
    }

    public void setPhoneNumbers(List<PhoneNumberView> phoneNumbers) {
        this.phoneNumbers = phoneNumbers;
    }

    public Set<String> getHobbies() {
        return hobbies;
    }

    public void setHobbies(Set<String> hobbies) {
        this.hobbies = hobbies;
    }
}
