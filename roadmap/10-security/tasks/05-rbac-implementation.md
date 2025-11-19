# Task 2.2: Role-Based Access Control (RBAC) Implementation

**Priority:** MEDIUM-HIGH
**Estimated Effort:** 2 weeks
**Dependencies:** Task 2.1 (Spring Security Integration)

---

## Overview

Implement comprehensive role-based access control for actors, including per-actor authorization rules, permission checking, configurable role hierarchies, and a default deny policy.

---

## Requirements

### 1. Authorization Manager

Core authorization logic:

```java
public interface AuthorizationManager {
    /**
     * Check if authentication has required permission
     */
    boolean hasPermission(Authentication auth, String permission);
    
    /**
     * Check if authentication has required role
     */
    boolean hasRole(Authentication auth, String role);
    
    /**
     * Get all permissions for an authentication
     */
    Set<String> getPermissions(Authentication auth);
}
```

### 2. Permission-Based Access Control

```java
@ActorSecured
@RequiresPermission("admin:write")
public class AdminActor implements SpringActor<Command> {
    // Actor implementation
}
```

### 3. Role Hierarchy

```yaml
spring:
  actor:
    security:
      role-hierarchy:
        ROLE_SUPER_ADMIN: ROLE_ADMIN, ROLE_USER
        ROLE_ADMIN: ROLE_USER
        ROLE_USER: ROLE_GUEST
```

### 4. Per-Actor Authorization Rules

```yaml
spring:
  actor:
    security:
      actors:
        AdminActor:
          roles: ROLE_ADMIN
          permissions:
            - admin:read
            - admin:write
        UserActor:
          roles: ROLE_USER
```

---

## Deliverables

1. ✅ `AuthorizationManager` interface and implementation
2. ✅ `@RequiresPermission` annotation
3. ✅ Role hierarchy configuration
4. ✅ Per-actor authorization rules
5. ✅ Default deny policy
6. ✅ Comprehensive tests

---

## Success Criteria

- [ ] Role-based access control works
- [ ] Permission-based access control works
- [ ] Role hierarchies are respected
- [ ] Default deny policy enforced
- [ ] All tests pass

