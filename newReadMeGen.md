# digital-tmp-multitenant — Architecture generique

Ce document decrit la **nouvelle architecture generique** du module apres nettoyage des personnalisations liees a l'Espace Educatif.

## 1) Objectif du module

`digital-tmp-multitenant` est une librairie Spring Boot qui apporte une infrastructure multi-tenant **schema-per-tenant** reutilisable pour plusieurs applications.

Fonctions principales :
- resolution du tenant courant depuis HTTP
- routage Hibernate vers le schema du tenant
- creation/migration des schemas avec Liquibase
- propagation du contexte tenant via Kafka/JMS/Feign
- cache keys tenant-aware
- scheduling multi-tenant
- verrous distribues JDBC

## 2) Ce qui a ete retire pour generericiser

Les composants specifiques "annee scolaire" ont ete supprimes :
- `src/main/java/net/atos/digital/multitenant/tenant/properties/TenantSchoolYear.java`
- `src/main/java/net/atos/digital/multitenant/tenant/properties/SchoolYearHistoryStatus.java`
- `src/main/java/net/atos/digital/multitenant/tenant/hibernate/schema/SchoolYearSchemaManager.java`
- `src/main/java/net/atos/digital/multitenant/tenant/migration/StatefulMigrationOrchestrator.java`

La logique associee a ete retiree de :
- `src/main/java/net/atos/digital/multitenant/tenant/properties/TenantData.java`
- `src/main/java/net/atos/digital/multitenant/tenant/properties/TenantConfigurationRegistry.java`
- `src/main/java/net/atos/digital/multitenant/tenant/hibernate/DatabaseSchemaInitializer.java`
- `src/main/java/net/atos/digital/multitenant/tenant/hibernate/schema/SchemaCreatorService.java`
- `src/main/java/net/atos/digital/multitenant/tenant/hibernate/schema/SchemaAdmin.java`
- `src/main/java/net/atos/digital/multitenant/event/NewTenantHandler.java`

## 3) Nouveau modele metier (generique)

### Tenant
Le record `Tenant` est maintenant minimal :

```java
public record Tenant(
    UUID id,
    String name,
    String schemaName
) {}
```

### TenantData
Le record `TenantData` devient generique :

```java
public record TenantData(
    UUID uuid,
    String tenantName,
    String displayName,
    boolean active
) {
    public Tenant toTenant() {
        return new Tenant(uuid, tenantName, tenantName);
    }
}
```

## 4) Nouveau flux de resolution tenant

1. `TenantFilter` lit uniquement `X-TENANT`
2. `TenantConfigurationRegistry` retrouve le `TenantData`
3. `TenantData.toTenant()` construit le `Tenant`
4. `TenantContext` stocke le tenant (ThreadLocal)
5. Hibernate utilise `schemaName` pour `SET search_path`
6. Le contexte est clear en fin de traitement

Headers retirees :
- `X-SCHOOL-YEAR`
- `schoolYear` (Kafka/JMS)

## 5) Architecture cible

```text
HTTP Request
  -> TenantFilter (X-TENANT)
  -> TenantContext (ThreadLocal)
  -> Hibernate MultiTenantConnectionProvider
  -> DB schema du tenant

Cross-cutting:
  - Feign interceptor: propage X-TENANT
  - Kafka/JMS converters: propage tenant
  - Cache key generator: inclut schema tenant
  - Scheduler: execute les jobs par tenant
  - Liquibase: migre tous les schemas identifies
```

## 6) Composants clefs conserves

- `src/main/java/net/atos/digital/multitenant/filter/TenantFilter.java`
- `src/main/java/net/atos/digital/multitenant/tenant/TenantContext.java`
- `src/main/java/net/atos/digital/multitenant/configurations/TenantJpaConfig.java`
- `src/main/java/net/atos/digital/multitenant/tenant/hibernate/SchemaBasedMultiTenantConnectionProvider.java`
- `src/main/java/net/atos/digital/multitenant/configurations/TenantLiquibaseConfig.java`
- `src/main/java/net/atos/digital/multitenant/tenant/properties/TenantConfigurationRegistrar.java`
- `src/main/java/net/atos/digital/multitenant/tenant/properties/TenantConfigurationRegistry.java`
- `src/main/java/net/atos/digital/multitenant/filter/TenantFeignInterceptor.java`
- `src/main/java/net/atos/digital/multitenant/tenant/kafka/MultitenantKafkaMessageConverter.java`
- `src/main/java/net/atos/digital/multitenant/tenant/jms/TenantMappingJackson2MessageConverter.java`
- `src/main/java/net/atos/digital/multitenant/tenant/cache/MultitenantKeyGenerator.java`
- `src/main/java/net/atos/digital/multitenant/lock/JdbcTableDistributedLockManager.java`

## 7) Configuration simplifiee

Points importants :
- suppression de `schema-configuration.manage-school-year`
- suppression des constantes `YEAR_SCHOOL` et `X_SCHOOL_YEAR`
- valeur par defaut des packages d'evenements rendue neutre dans `TenantAppEventConfig`
- changelog public externalise dans `PublicSchemaLiquibaseConfig`:
  - `multitenant.public-schema.name` (defaut `public`)
  - `multitenant.public-schema.changelog` (defaut `classpath:/db/changelog/multitenant-changelog.xml`)

## 8) Validation technique

Build effectue apres refactoring :
- commande : `mvn clean install`
- resultat : `BUILD SUCCESS`

## 9) Benefices de la version generique

- reduction forte du couplage metier
- API plus simple (`Tenant`, `TenantData`, headers)
- adoption plus facile dans d'autres applications
- maintenance et evolutivite ameliorees

## 10) Prochaines evolutions recommandees

1. Ajouter des tests unitaires/integration pour `TenantFilter`, converters Kafka/JMS, et `SchemaCreatorService`.
2. Introduire une option de compatibilite transitoire si certaines apps envoient encore `X-SCHOOL-YEAR`.
3. Completer la documentation d'integration cote applications clientes (exemples de payload Config Server, bonnes pratiques de nommage schema).