package code.with.vanilson.productservice;

/**
 * ProductStatus — Fase 3 (Marketplace Role Capabilities): product lifecycle status.
 * <p>
 * {@code ACTIVE} products behave exactly as before this field existed. {@code SUSPENDED}
 * products are hidden from the public catalogue/search/detail (except to their owner and
 * ADMIN), and are rejected on the purchase path (REST purchase + Kafka inventory
 * reservation) — see the Fase 3 tasks in
 * {@code docs/superpowers/plans/2026-07-11-marketplace-role-capabilities.md}.
 * <p>
 * Stored as VARCHAR via {@code @Enumerated(EnumType.STRING)}; column added by migration
 * {@code V12__add_status_to_product.sql} with DB default 'ACTIVE' so every pre-existing
 * row is grandfathered as ACTIVE.
 *
 * @author vamuhong
 */
public enum ProductStatus {
    ACTIVE,
    SUSPENDED
}
