# Integration tests

Black-box Rest Assured tests for the services listed in `tracked-repos.json`. The tests only talk to
the services over HTTP; they never import service code.

## Layout

- `src/test/java/com/example/it/support/`: shared helpers. `Specs` holds one Rest Assured request
  spec per service, `ServiceEndpoints` resolves base URLs, `CatalogFixtures` creates test data.
- `src/test/java/com/example/it/catalog/`: catalog-service only, one class per resource or concern.
- `src/test/java/com/example/it/order/`: order-service only.
- `src/test/java/com/example/it/flows/`: behaviour that spans services.

## Conventions

- Test classes end in `IT` so failsafe runs them (`mvn verify`); annotate each with a `@DisplayName`
  naming the service and concern.
- Start every request from `given(Specs.catalog())` or `given(Specs.order())`; never hardcode URLs.
- Every test creates the data it needs through the public API (for example
  `CatalogFixtures.createBook`) with unique values, and never relies on seed data or test order.
- Assert status codes and the fields the consumer depends on with Hamcrest matchers in the `then()`
  block; use AssertJ for checks that need several calls.
- Error responses are RFC 9457 problem details: assert `status` and, where it is stable, `detail`.
- Money comes back as a JSON number; compare with float literals such as `equalTo(42.5f)`.
- Add a helper to `support/` only when two or more test classes need it.

## Running locally

```bash
python3 scripts/tracked_repos.py clone   # or point start-services.sh at your own checkouts
scripts/start-services.sh                # builds and starts both services, waits for health
mvn verify
scripts/stop-services.sh
```
