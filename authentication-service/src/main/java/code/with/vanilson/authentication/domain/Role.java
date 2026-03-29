package code.with.vanilson.authentication.domain;

/**
 * Role — Domain Enum
 * <p>
 * USER   → standard customer (place orders, view own data)
 * SELLER → can manage own product catalog
 * ADMIN  → full platform access
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
public enum Role {
    USER,
    SELLER,
    ADMIN
}
