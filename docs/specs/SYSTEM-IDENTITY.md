# System Specification — Identity

Status: **APPROVED CONTRACT; IMPLEMENTATION STARTS M2**

## Domain contract

`User` is a person, `Business` a tenant, `BusinessMembership` authorization, and `Device` an Android installation. They remain separate.

## Authentication and authorization

Keycloak is the IdP; Spring Security is an OAuth2/OIDC resource server. Validated JWT `sub` maps uniquely to `User.externalSubject`. Email/name are attributes. Authentication grants no business access: tenant access requires active membership. `storeId`, `deviceId`, headers, and payload identifiers cannot establish authority.

## Bootstrap states

`BUSINESS_REQUIRED`, `LOCAL_BUSINESS_LINK_REQUIRED`, `READY`.

## API allocation

M2 configures resource-server/user mapping; M3 owns business APIs; M4 owns device link; M5 owns `POST /api/v1/bootstrap`.
