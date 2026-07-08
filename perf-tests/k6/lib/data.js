// Queries, mutations and input builders shared by all k6 scripts.

export const ALL_PERSONS = `{
  allPersons {
    id fullName age monthlyNetSalary bmi
    address { city country }
    phoneNumbers { type number }
    hobbies
  }
}`;

export const PERSON_BY_ID = `query($id: ID!) {
  personById(id: $id) { id fullName age yearsOfService salary monthlyNetSalary }
}`;

export const PERSONS_BY_CITY = `query($city: String!) {
  personsByCity(city: $city) { id fullName address { city } }
}`;

export const CREATE_PERSON = `mutation($input: CreatePersonInput!) {
  createPerson(input: $input) { id fullName age monthlyNetSalary }
}`;

export const UPDATE_SALARY = `mutation($id: ID!, $salary: Float!) {
  updatePersonSalary(id: $id, salary: $salary) { id salary monthlyNetSalary }
}`;

export const DELETE_PERSON = `mutation($id: ID!) {
  deletePerson(id: $id)
}`;

export const SEEDED_IDS = ['1', '2', '3'];
export const SEEDED_CITIES = ['Tel Aviv', 'Haifa'];

// Every VU/iteration combination gets a unique email so createPerson never
// trips the uniqueness rule under load.
export function validCreateInput(vu, iter) {
  return {
    firstName: 'Load',
    lastName: `Tester${vu}`,
    email: `perf-${vu}-${iter}-${Date.now()}@example.com`,
    birthDate: '1990-04-15',
    gender: 'OTHER',
    salary: 480000,
    hireDate: '2020-01-01',
    heightCm: 175,
    weightKg: 70.5,
    address: { street: 'Perf St', houseNumber: 42, city: 'Load City', zipCode: '12345', country: 'Testland' },
    phoneNumbers: [{ type: 'MOBILE', number: '+972-50-0000000' }],
    hobbies: ['benchmarking'],
  };
}

// GraphQL-valid but breaks the server-side JSON schema (heightCm > 260,
// 1-char hobby): must be rejected fast with SCHEMA_VALIDATION_FAILED.
export function invalidCreateInput(vu, iter) {
  const input = validCreateInput(vu, iter);
  input.heightCm = 300;
  input.hobbies = ['x'];
  return input;
}

export function randomItem(array) {
  return array[Math.floor(Math.random() * array.length)];
}
