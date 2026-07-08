// Load test with three parallel scenarios treating the service as a black box:
//
//   read_load           ramping VUs running the read queries (the common case)
//   write_load          constant arrival rate of create -> update -> delete flows
//   validation_rejects  constant probing with schema-invalid input; rejects must
//                       stay fast (validation happens before any DB work)
//
//   k6 run perf-tests/k6/load.js                 (~2 minutes)
//   k6 run -e QUICK=1 perf-tests/k6/load.js      (~20 seconds, CI/sanity)
//   k6 run -e BASE_URL=http://host:8080 perf-tests/k6/load.js
import { check } from 'k6';
import { gql, hasData, hasNoErrors, firstErrorLiteral } from './lib/graphql.js';
import {
  ALL_PERSONS, PERSON_BY_ID, PERSONS_BY_CITY,
  CREATE_PERSON, UPDATE_SALARY, DELETE_PERSON,
  SEEDED_IDS, SEEDED_CITIES,
  validCreateInput, invalidCreateInput, randomItem,
} from './lib/data.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const QUICK = !!__ENV.QUICK;

const readStages = QUICK
  ? [{ duration: '5s', target: 5 }, { duration: '10s', target: 5 }, { duration: '5s', target: 0 }]
  : [{ duration: '30s', target: 20 }, { duration: '60s', target: 20 }, { duration: '15s', target: 0 }];
const flatDuration = QUICK ? '20s' : '105s';

export const options = {
  scenarios: {
    read_load: {
      executor: 'ramping-vus',
      exec: 'readFlow',
      startVUs: 0,
      stages: readStages,
    },
    write_load: {
      executor: 'constant-arrival-rate',
      exec: 'writeFlow',
      rate: QUICK ? 5 : 10,
      timeUnit: '1s',
      duration: flatDuration,
      preAllocatedVUs: 10,
      maxVUs: 50,
    },
    validation_rejects: {
      executor: 'constant-vus',
      exec: 'validationFlow',
      vus: QUICK ? 2 : 5,
      duration: flatDuration,
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    'http_req_duration{scenario:read_load}': ['p(95)<250', 'p(99)<500'],
    'http_req_duration{scenario:write_load}': ['p(95)<400'],
    // schema validation happens before service/DB work - rejects must be fast
    'http_req_duration{scenario:validation_rejects}': ['p(95)<200'],
  },
};

export function readFlow() {
  const pick = Math.random();
  if (pick < 0.5) {
    const r = gql(BASE_URL, ALL_PERSONS);
    check(r.body, { 'read: allPersons ok': (b) => hasData(b, 'allPersons') });
  } else if (pick < 0.8) {
    const r = gql(BASE_URL, PERSON_BY_ID, { id: randomItem(SEEDED_IDS) });
    check(r.body, { 'read: personById ok': (b) => hasData(b, 'personById') });
  } else {
    const r = gql(BASE_URL, PERSONS_BY_CITY, { city: randomItem(SEEDED_CITIES) });
    check(r.body, { 'read: personsByCity ok': (b) => hasData(b, 'personsByCity') });
  }
}

export function writeFlow() {
  const created = gql(BASE_URL, CREATE_PERSON, { input: validCreateInput(__VU, __ITER) });
  const ok = check(created.body, { 'write: created': (b) => hasData(b, 'createPerson') });
  if (!ok) {
    return;
  }
  const id = created.body.data.createPerson.id;

  const updated = gql(BASE_URL, UPDATE_SALARY, { id, salary: 100000 + Math.random() * 900000 });
  check(updated.body, { 'write: salary updated': (b) => hasData(b, 'updatePersonSalary') });

  // delete keeps the in-memory dataset stable across the whole run
  const deleted = gql(BASE_URL, DELETE_PERSON, { id });
  check(deleted.body, { 'write: deleted': (b) => hasNoErrors(b) && b.data.deletePerson === true });
}

export function validationFlow() {
  const rejected = gql(BASE_URL, CREATE_PERSON, { input: invalidCreateInput(__VU, __ITER) });
  check(rejected.body, {
    'validation: rejected with SCHEMA_VALIDATION_FAILED': (b) => firstErrorLiteral(b) === 'SCHEMA_VALIDATION_FAILED',
    'validation: details present': (b) =>
      b.errors && b.errors[0].extensions.details && b.errors[0].extensions.details.length > 0,
  });
}
