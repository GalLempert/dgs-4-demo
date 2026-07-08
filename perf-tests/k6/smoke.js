// Smoke test: 1 virtual user, ~10 seconds. Verifies every operation works
// end-to-end and responds quickly before any heavier scenario is worth running.
//
//   k6 run perf-tests/k6/smoke.js
//   k6 run -e BASE_URL=http://host:8080 perf-tests/k6/smoke.js
import { check } from 'k6';
import { gql, hasData, hasNoErrors, firstErrorLiteral } from './lib/graphql.js';
import {
  ALL_PERSONS, PERSON_BY_ID, PERSONS_BY_CITY,
  CREATE_PERSON, UPDATE_SALARY, DELETE_PERSON,
  validCreateInput, invalidCreateInput,
} from './lib/data.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: 1,
  duration: '10s',
  thresholds: {
    checks: ['rate==1.0'],
    http_req_failed: ['rate==0'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const reads = gql(BASE_URL, ALL_PERSONS);
  check(reads.body, {
    'allPersons returns data': (b) => hasData(b, 'allPersons') && b.data.allPersons.length > 0,
    'allPersons has computed fields': (b) => hasNoErrors(b) && b.data.allPersons[0].age > 0,
  });

  const byId = gql(BASE_URL, PERSON_BY_ID, { id: '1' });
  check(byId.body, { 'personById works': (b) => hasData(b, 'personById') });

  const byCity = gql(BASE_URL, PERSONS_BY_CITY, { city: 'Tel Aviv' });
  check(byCity.body, { 'personsByCity works': (b) => hasData(b, 'personsByCity') });

  const created = gql(BASE_URL, CREATE_PERSON, { input: validCreateInput(__VU, __ITER) });
  check(created.body, { 'createPerson works': (b) => hasData(b, 'createPerson') });

  const id = created.body.data ? created.body.data.createPerson.id : undefined;
  if (id) {
    const updated = gql(BASE_URL, UPDATE_SALARY, { id, salary: 500000 });
    check(updated.body, { 'updatePersonSalary works': (b) => hasData(b, 'updatePersonSalary') });

    const deleted = gql(BASE_URL, DELETE_PERSON, { id });
    check(deleted.body, { 'deletePerson works': (b) => hasNoErrors(b) && b.data.deletePerson === true });
  }

  const rejected = gql(BASE_URL, CREATE_PERSON, { input: invalidCreateInput(__VU, __ITER) });
  check(rejected.body, {
    'invalid input rejected by JSON schema': (b) => firstErrorLiteral(b) === 'SCHEMA_VALIDATION_FAILED',
  });
}
