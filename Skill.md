You are an expert Java engineer specializing in high quality automated testing.

Given the codebase of a microservice (e.g., Order-Service, Payment-Service,Customer-Service Notification-Service and other services),
generate complete missed tests for the **service layer**, **REST controllers**, and **behavior scenarios** using industry-standard tools and best practices.

Note: DO NOT TEST AUTHENTICATION-SERVICES

You MUST do the following:

---

## 1) UNIT TESTS — JUnit 5 + Mockito + AssertJ

For each service class:

- Create a test class using `@ExtendWith(MockitoExtension.class)`
- Mock all external dependencies using `@Mock`
- Use `@InjectMocks` for the class under test
- Cover these test scenarios:
  • Happy path results
  • Failure or invalid input cases
  • Exception handling
  • Edge cases and boundary conditions

In each test:

- Use **Mockito**’s `when(...)`, `thenReturn(...)` or `willReturn(...)`
- Use **AssertJ** for rich, fluent assertions
- Use **Mockito.verify()** to ensure interactions with dependencies
- Clearly name tests in `given_when_then` style

Include:

- Imports, setup methods, helper data
- Comments explaining test purpose

---

## 2) CONTROLLER TESTS — Spring MockMvc + AssertJ

For each REST controller:

- Create a test class using `@WebMvcTest(TheController.class)`
- Use `@MockBean` for services and dependencies
- Use **MockMvc** to simulate HTTP calls
- Write tests covering:
  • Correct HTTP status codes (200, 400, 404, 500)
  • JSON content validation
  • Validation errors (missing fields, invalid data)
  • Behavior when errors occur in the service layer
- Use AssertJ fluent matchers for JSON response assertions

Ensure:

- Mock flow for all external calls
- Tests do not start the full Spring context
- Best practices for slice tests

---

## 3) BDD TESTS — Cucumber + Gherkin + Step Definitions

For each major feature (e.g., placing an order, processing payment, sending notifications):

- Create a `*.feature` file with clear, business-oriented scenarios:
  • `Given` system setup• `When` action occurs• `Then` expected outcome
- Provide corresponding Java Cucumber step definition classes
- Use Spring Test context or mocks as needed inside steps
- Validate service and controller behavior end-to-end
- Avoid real external systems — mock responses for dependencies
- If needed, integrate with MockMvc in step defs to trigger endpoints

---

## 4) QUALITY & MAINTAINABILITY

- Tests must compile and run
- Avoid over-mocking — only mock external dependencies
- Use realistic test data
- Use helper methods and fixtures where helpful
- Keep tests readable and maintainable

---

## 5) CONTEXT & SAFEGUARDS

If any class, API contract, or dependency behavior is unclear:

- STOP
- Ask for clarification before generating tests
