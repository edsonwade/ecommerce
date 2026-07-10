# ============================================================
# Feature: Product tenant isolation (B3 Fase 1b)
# A by-id product read must honour the caller's bound tenant —
# one tenant can never read another tenant's product by guessing
# its id. Backs the tenant-scoped getProductById read path.
# ============================================================
@TenantIsolation
Feature: Product reads are isolated per tenant
  As a SaaS platform operator
  I want each tenant's product reads scoped to that tenant
  So that one tenant can never read another tenant's catalogue

  @TenantIsolation
  Scenario: A tenant can read its own product by id
    Given product 100 belongs to tenant "tenant-a"
    When tenant "tenant-a" requests product 100
    Then the product read succeeds

  @TenantIsolation
  Scenario: A tenant cannot read another tenant's product by id
    Given product 100 belongs to tenant "tenant-a"
    When tenant "tenant-b" requests product 100
    Then the product read is not found

  @TenantIsolation
  Scenario: A tenant reading a non-existent id is not found
    Given product 100 belongs to tenant "tenant-a"
    When tenant "tenant-a" requests product 999
    Then the product read is not found
