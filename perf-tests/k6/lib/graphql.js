// Minimal GraphQL client for k6: single POST helper returning both the raw
// http response (for latency/status metrics) and the parsed body (for checks).
import http from 'k6/http';

export function gql(baseUrl, query, variables = {}, tags = {}) {
  const res = http.post(
    `${baseUrl}/graphql`,
    JSON.stringify({ query, variables }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags,
    }
  );
  let body = {};
  try {
    body = res.json();
  } catch (e) {
    // leave body empty; checks on it will fail and surface the problem
  }
  return { res, body };
}

export function hasData(body, field) {
  return body && body.data && body.data[field] !== undefined && body.data[field] !== null;
}

export function hasNoErrors(body) {
  return body && !body.errors;
}

export function firstErrorLiteral(body) {
  return body && body.errors && body.errors[0] && body.errors[0].extensions
    ? body.errors[0].extensions.literal
    : undefined;
}
