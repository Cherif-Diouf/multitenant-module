# digital-tmp-multitenant

> **Librairie Spring Boot — Gestion Multi-Tenant par schéma de base de données**
>
> Version : `1.0.0-SNAPSHOT` | Java 21 | Spring Boot 3.5.x | Jakarta EE

---

## Table des matières

1. [Vue d'ensemble](#1-vue-densemble)
2. [Architecture générale](#2-architecture-générale)
3. [Modes de fonctionnement](#3-modes-de-fonctionnement)
4. [Composants principaux](#4-composants-principaux)
5. [Configuration](#5-configuration)
6. [Guide d'intégration](#6-guide-dintégration)
7. [Analyse critique — Couplages spécifiques à l'Espace Éducatif](#7-analyse-critique--couplages-spécifiques-à-lespace-éducatif)
8. [Recommandations de nettoyage pour généricisation](#8-recommandations-de-nettoyage-pour-généricisation)
9. [Dépendances](#9-dépendances)
10. [Build & Publication](#10-build--publication)

---

## 1. Vue d'ensemble

`digital-tmp-multitenant` est une **librairie transverse** destinée à être importée dans d'autres projets Spring Boot afin de leur apporter une infrastructure multi-tenant complète, basée sur la stratégie **SCHEMA par tenant** d'Hibernate.

### Ce que la librairie fait

- Résolution du tenant courant à chaque requête HTTP (via un header HTTP dédié)
- Routage automatique de toutes les connexions JPA/Hibernate vers le schéma de base de données du tenant identifié
- Initialisation et migration des schémas via **Liquibase** (un schéma par tenant)
- Propagation du contexte tenant dans les messages **Kafka**, **JMS/ActiveMQ**
- Propagation du contexte tenant dans les appels **Feign** inter-services
- Génération de clés de cache (**Spring Cache**) tenant-aware
- Exécution de tâches planifiées (**`@TenantScheduled`**) sur tous les tenants en parallèle
- Verrous distribués via JDBC (`JdbcTableDistributedLockManager`)
- Intégration **ShedLock** pour les schedulers distribués
- Rechargement périodique du référentiel de tenants depuis un **Spring Cloud Config Server**
- Publication d'événements applicatifs tenant-aware (proxy `ApplicationEventPublisher`)
- Support **Swagger/SpringDoc** : injection automatique des headers tenant dans l'UI

### Ce qu'elle ne fait PAS (par conception)

- Elle ne gère pas l'authentification ou l'autorisation
- Elle ne crée pas les données métier initiales
- Elle ne gère pas les bases de données séparées par tenant (uniquement schema-per-tenant)

---

## 2. Architecture générale

```
┌──────────────────────────────────────────────────────────────────────┐
│                        Application cliente                           │
│                                                                      │
│   @SpringBootApplication                                             │
│   @EnableSidebar          ◄──── Point d'entrée de la librairie      │
│   @EnableScheduling                                                  │
└─────────────────────────────┬────────────────────────────────────────┘
                              │ importe
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│                    digital-tmp-multitenant                           │
│                                                                      │
│  ┌──────────────┐    ┌─────────────────┐    ┌──────────────────┐   │
│  │ TenantFilter │    │  TenantContext  │    │TenantConfigReg.  │   │
│  │ (HTTP layer) │───►│ (ThreadLocal)   │◄───│(Référentiel      │   │
│  └──────────────┘    └────────┬────────┘    │ tenants)         │   │
│                               │             └────────┬─────────┘   │
│                               │                      │fetch        │
│                               ▼                      ▼             │
│  ┌─────────────────────────────────┐    ┌────────────────────────┐ │
│  │  SchemaBasedMultiTenant         │    │  Config Server         │ │
│  │  ConnectionProvider (Hibernate) │    │  (Spring Cloud Config) │ │
│  └─────────────────────────────────┘    └────────────────────────┘ │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │  Kafka       │  │  JMS         │  │  Cache       │             │
│  │  Converter   │  │  Converter   │  │  KeyGen      │             │
│  └──────────────┘  └──────────────┘  └──────────────┘             │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  Liquibase : MultiTenantSpringLiquibase                       │  │
│  │  (exécute le changelog sur chaque schéma tenant)              │  │
│  └───────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     PostgreSQL                                       │
│                                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────────┐   │
│  │ public   │  │ tenant_a │  │ tenant_b │  │ tenant_c         │   │
│  │ (shared) │  │ (schema) │  │ (schema) │  │ (schema)         │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
```

### Flux d'une requête HTTP

```
Requête HTTP
    │  Header: X-TENANT: tenant_a
    │  Header: X-SCHOOL-YEAR: 2024  (spécifique — voir §7)
    ▼
TenantFilter.doFilterInternal()
    │  → Résout le TenantData depuis TenantConfigurationRegistry
    │  → Construit l'objet Tenant (id, name, schemaName)
    │  → TenantContext.setCurrentTenant(tenant)  [ThreadLocal]
    ▼
Controller / Service / Repository
    │
    ▼
SchemaBasedMultiTenantConnectionProvider
    │  → Ouvre connexion JDBC
    │  → SET search_path TO 'tenant_a'  (PostgreSQL)
    │  → Toutes les requêtes JPA sont dirigées vers le bon schéma
    ▼
Base de données — schéma tenant_a
```

---

## 3. Modes de fonctionnement

La librairie supporte **deux modes**, activés via les profils Spring :

| Profil Spring | Constante | Description |
|---|---|---|
| `multiSchema` | `ProfileConstants.MULTI_SCHEMA` | Une seule entité "tenant", plusieurs schémas (ex: un schéma par année ou par contexte) |
| `multitenant` | `ProfileConstants.MULTI_TENANT` | Plusieurs tenants distincts, chacun avec son propre schéma |

Les beans conditionnés par `@Profile("multiSchema | multitenant")` sont actifs dans les deux cas.

### Mode `multitenant`

- Chaque tenant est identifié par un `UUID` et un nom (`tenantName`)
- Le header HTTP `X-TENANT` doit contenir le nom du tenant
- Le `TenantConfigurationRegistry` est alimenté depuis le Config Server
- Chaque tenant a son propre schéma PostgreSQL nommé exactement `tenantName`

### Mode `multiSchema`

- Un seul tenant "logique" (configuré dans `schema-configuration.default-schema`)
- Plusieurs schémas physiques (ex: `public`, `schema_custom`)
- Le header `X-TENANT` peut contenir le nom du schéma cible

---

## 4. Composants principaux

### 4.1 Couche contexte — `TenantContext`

```
net.atos.digital.multitenant.tenant.TenantContext
```

- Stocke le `Tenant` courant dans un `InheritableThreadLocal` (propagé aux threads enfants)
- Enrichit le MDC (Mapped Diagnostic Context) de SLF4J avec le nom du tenant pour les logs
- Expose : `getCurrentTenant()`, `getCurrentTenantName()`, `getCurrentSchemaName()`
- Méthode `clear()` toujours appelée en fin de requête (dans le finally du filter)

**Le record `Tenant`** :
```java
public record Tenant(UUID id, String name, String schoolYear, String schemaName)
```
> ⚠️ Le champ `schoolYear` est spécifique à l'Espace Éducatif — voir §7.

---

### 4.2 Filtre HTTP — `TenantFilter`

```
net.atos.digital.multitenant.filter.TenantFilter
```

- `OncePerRequestFilter` activé uniquement sur les profils `multiSchema | multitenant`
- Lit les headers **`X-TENANT`** et **`X-SCHOOL-YEAR`** (ce dernier spécifique — voir §7)
- Résout le `TenantData` depuis le `TenantConfigurationRegistry`
- Construit l'objet `Tenant` et le stocke dans `TenantContext`
- Exclut automatiquement les URLs Swagger, Actuator, et les chemins configurés

**Paths exclus par défaut** (non soumis au filtre) :
```
/actuator/**, /swagger-ui.html, /swagger-ui/**, /v3/api-docs/**, /v2/api-docs
```

**Chemins supplémentaires exclus** : configurables via `schema-configuration.filters.exclude-paths-filter`

---

### 4.3 Référentiel tenants — `TenantConfigurationRegistry` & `TenantConfigurationRegistrar`

```
net.atos.digital.multitenant.tenant.properties.*
```

**`TenantConfigurationRegistry`** : singleton en mémoire contenant :
- La liste des `TenantData` (tenants connus)
- Les propriétés de configuration par tenant (`Map<tenantName, Map<key, value>>`)
- Les objets de configuration bindés par classe (`Map<tenantName, Map<Class, instance>>`)

**`TenantConfigurationRegistrar`** : composant qui :
- Se connecte au **Spring Cloud Config Server** (`spring.config.import`) pour récupérer le référentiel via REST (`GET /api/registry/{applicationName}`)
- Initialise le registry au démarrage (`InitializingBean.afterPropertiesSet()`)
- **Rafraîchit automatiquement** le registry toutes les **5 minutes**
- Publie un `NewTenantsEvent` si de nouveaux tenants sont détectés
- Scanne les classes annotées `@TenantConfigurationProperties` dans les packages de l'application cliente pour binder les propriétés par tenant

**Annotation `@TenantConfigurationProperties`** : à placer sur une classe de l'application cliente pour que ses propriétés soient bindées par tenant depuis le Config Server.

---

### 4.4 Couche JPA/Hibernate — `TenantJpaConfig`

```
net.atos.digital.multitenant.configurations.TenantJpaConfig
net.atos.digital.multitenant.tenant.hibernate.*
```

- Configure Hibernate en mode `SCHEMA` multi-tenant
- **`SchemaBasedMultiTenantConnectionProvider`** : implémente `MultiTenantConnectionProvider` d'Hibernate. Pour chaque connexion, exécute `SET search_path TO '<schemaName>'` (PostgreSQL) ou `SET SCHEMA '<schemaName>'` (H2)
- **`CurrentTenantIdentifierResolverImpl`** : retourne le nom du schéma du tenant courant depuis `TenantContext`
- **`SchemaSelector`** : interface fonctionnelle qui abstrait la commande SQL de sélection de schéma (H2 vs PostgreSQL)
- Supprime toute configuration `ddl-auto` par sécurité — Liquibase prend en charge la DDL

---

### 4.5 Initialisation des schémas — `DatabaseSchemaInitializer` & Liquibase

```
net.atos.digital.multitenant.tenant.hibernate.DatabaseSchemaInitializer
net.atos.digital.multitenant.configurations.TenantLiquibaseConfig
net.atos.digital.multitenant.event.PublicSchemaLiquibaseConfig
```

- **`SchemaCreatorService`** : calcule la liste de tous les schémas à créer/migrer
- **`DatabaseSchemaInitializer`** : crée les schémas manquants en base avant le démarrage de Liquibase
- **`TenantLiquibaseConfig`** : configure `MultiTenantSpringLiquibase` (Liquibase officiel) pour exécuter le changelog sur chaque schéma tenant
- **`PublicSchemaLiquibaseConfig`** : au `ApplicationReadyEvent`, exécute le changelog `multitenant-changelog.xml` sur le schéma `public` (schéma partagé)

> La librairie embarque son propre changelog Liquibase (`db/changelog/multitenant-changelog.xml`) qui crée les tables d'infrastructure (audit, verrous, etc.) dans le schéma public.

---

### 4.6 Kafka — `TenantKafkaConfig`

```
net.atos.digital.multitenant.configurations.TenantKafkaConfig
net.atos.digital.multitenant.tenant.kafka.*
```

- Crée un `ProducerFactory` et un `ConsumerFactory` qui sérialisent/désérialisent en JSON
- **`MultitenantKafkaMessageConverter`** : enrichit chaque message Kafka avec le header tenant
- **`TenantKafkaRecordInterceptor`** : à la réception d'un message, extrait le header tenant et alimente le `TenantContext`
- Configure une Dead Letter Queue avec `DeadLetterPublishingRecoverer` et `FixedBackOff`

---

### 4.7 JMS/ActiveMQ — `TenantJmsConfig`

```
net.atos.digital.multitenant.configurations.TenantJmsConfig
net.atos.digital.multitenant.tenant.jms.*
```

- **`TenantMappingJackson2MessageConverter`** : ajoute le header tenant dans chaque message JMS sortant
- **`MultitenantSimpleMessageConverter`** : à la réception, extrait le header et positionne le `TenantContext`
- Configure deux `JmsTemplate` : un pour les queues, un pour les topics (pub/sub)

---

### 4.8 Feign — `TenantFeignInterceptor`

```
net.atos.digital.multitenant.filter.TenantFeignInterceptor
```

- Implémente `feign.RequestInterceptor`
- Pour chaque appel Feign sortant, injecte les headers `X-TENANT` et `X-SCHOOL-YEAR` depuis le `TenantContext`
- Assure la propagation du contexte tenant dans les appels inter-microservices

---

### 4.9 Cache — `MultitenantKeyGenerator`

```
net.atos.digital.multitenant.tenant.cache.MultitenantKeyGenerator
net.atos.digital.multitenant.configurations.TenantCacheConfig
```

- Bean nommé **`multitenantKeyGenerator`**, à utiliser avec `@Cacheable(keyGenerator = "multitenantKeyGenerator")`
- La clé de cache est composée de : `[className, methodName, currentSchemaName, params...]`
- Garantit l'isolation des entrées de cache entre tenants

---

### 4.10 Scheduling multi-tenant — `@TenantScheduled`

```
net.atos.digital.multitenant.tenant.scheduling.*
net.atos.digital.multitenant.configurations.TenantSchedulingConfig
```

- **`@TenantScheduled`** : annotation à utiliser à la place de `@Scheduled` dans l'application cliente
- Wraps un `@Scheduled` standard
- En mode multitenant actif : la tâche est exécutée **une fois par tenant**, avec le `TenantContext` positionné
- En mode non-multitenant : comportement identique à `@Scheduled` standard
- `TenantScheduledAnnotationBeanPostProcessor` étend `ScheduledAnnotationBeanPostProcessor` pour intercepter les méthodes annotées

**Usage dans l'application cliente :**
```java
@TenantScheduled(scheduled = @Scheduled(cron = "0 0 2 * * ?"))
public void nettoyageQuotidien() {
    // Sera exécuté pour chaque tenant avec le bon TenantContext
}
```

---

### 4.11 Verrous distribués — `DistributedLockManager`

```
net.atos.digital.multitenant.lock.*
```

- Interface `DistributedLockManager` avec sa méthode `acquire(lockKey, ttl)`
- Implémentation `JdbcTableDistributedLockManager` : utilise une table en base (`app_lock`) comme verrou distribué
- Le verrou implémente `AutoCloseable` → usage en `try-with-resources` pour libération automatique
- Nettoyage automatique des verrous expirés à chaque tentative d'acquisition
- L'identifiant du propriétaire (`ownerId`) est composé du hostname + PID pour unicité

**Tables requises (à créer via Liquibase) :**
```sql
CREATE TABLE "public"."app_lock" (
    lock_key VARCHAR(255) PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL
);
```

---

### 4.12 ShedLock — `ShedlockJpaConfig`

```
net.atos.digital.multitenant.configurations.ShedlockJpaConfig
```

- Activé par la propriété `jpa.shedlock.enabled=true`
- Configure ShedLock avec un `LockProvider` qui utilise le schéma du tenant courant
- `SchemaSelectedMultitenantDataSource` : DataSource décoratrice qui sélectionne dynamiquement le schéma avant chaque connexion

---

### 4.13 Publication d'événements — `TenantAppEventConfig`

```
net.atos.digital.multitenant.configurations.TenantAppEventConfig
net.atos.digital.multitenant.tenant.appevent.*
```

- Crée un proxy JDK autour de `ApplicationEventPublisher` via `MultiTenantEventPublisherProxy`
- Les événements des packages autorisés (`multitenant.events.allowed-packages`) sont publiés avec le contexte tenant
- Si le bean `appEventThreadConfig` (un `ExecutorService`) est présent, crée un `MultitenantEventNotifier` pour la publication asynchrone
- Les autres événements sont publiés directement (comportement standard)

---

### 4.14 Swagger/SpringDoc — `SwaggerUtils`

```
net.atos.digital.multitenant.tenant.SwaggerUtils
```

- Implémente `OperationCustomizer` de SpringDoc
- Injecte automatiquement les paramètres headers `X-TENANT` et `X-SCHOOL-YEAR` dans chaque opération de l'UI Swagger
- Alimente les exemples Swagger avec la liste des tenants et des codes d'année disponibles

---

### 4.15 Activation — `@EnableSidebar`

```
net.atos.digital.multitenant.annotations.EnableSidebar
```

Annotation de type "starter" à placer sur la classe principale de l'application cliente :

```java
@SpringBootApplication
@EnableSidebar
public class MonApplication { ... }
```

Elle importe `SidebarConfiguration` qui déclenche :
- Le `@ComponentScan` sur `net.atos.digital.multitenant`
- Le `@ConfigurationPropertiesScan` sur `net.atos.digital.multitenant.properties`

---

## 5. Configuration

### 5.1 Propriétés principales

```yaml
# --- Profil à activer ---
spring:
  profiles:
    active: multitenant   # ou: multiSchema

# --- Source de configuration des tenants ---
spring:
  config:
    import: "configserver:http://mon-config-server:8888"
  application:
    name: mon-application  # Utilisé pour construire l'URL /api/registry/{nom}

# --- Configuration des schémas ---
schema-configuration:
  default-schema:
    schema-name: public        # Schéma par défaut (mode multiSchema)
    liquibase-enabled: true
    contexts: []
    exclude-paths-filter: []
  schemas:                     # Schémas additionnels (optionnel)
    - schema-name: schema_extra
      liquibase-enabled: true
      contexts: ["ctx1"]
  manage-school-year: false    # ⚠️  Spécifique Espace Éducatif — voir §7
  exclude-tables-for-migration:
    - databasechangelog
    - databasechangeloglock
  filters:
    exclude-paths-filter:
      - /api/public/**
  readiness:
    check-liquibase-lock: true
    required-tag: ""
    required-label: ""

# --- Audit des migrations ---
app:
  migration:
    audit:
      schema: public
      table: migration_audit_log

# --- Verrous distribués ---
app:
  lock:
    schema: public
    table: app_lock

# --- ShedLock ---
jpa:
  shedlock:
    enabled: true

# --- Événements applicatifs multi-tenant ---
multitenant:
  events:
    allowed-packages:
      - com.monentreprise.monapplication    # packages dont les événements doivent être tenant-aware
```

### 5.2 Tables d'infrastructure (à créer via Liquibase)

La librairie s'appuie sur les tables suivantes dans le schéma `public` :

| Table | Usage |
|---|---|
| `app_lock` | Verrous distribués (`JdbcTableDistributedLockManager`) |
| `migration_audit_log` | Journal d'audit des migrations de schémas |
| `shedlock` | ShedLock (si activé) |

Le changelog interne de la librairie (`db/changelog/multitenant-changelog.xml`) est en charge de les créer.

---

## 6. Guide d'intégration

### Étape 1 — Ajouter la dépendance Maven

```xml
<dependency>
    <groupId>net.atos.digital</groupId>
    <artifactId>multitenant</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Étape 2 — Activer la librairie

```java
@SpringBootApplication
@EnableSidebar
public class MonApplication {
    public static void main(String[] args) {
        SpringApplication.run(MonApplication.class, args);
    }
}
```

### Étape 3 — Choisir un profil

```yaml
spring:
  profiles:
    active: multitenant
```

### Étape 4 — Configurer le Config Server

```yaml
spring:
  config:
    import: "configserver:http://mon-config-server:8888"
  application:
    name: mon-application
```

Le Config Server doit exposer une route `GET /api/registry/{applicationName}` retournant un `TenantConfigurationDTO` :

```json
{
  "tenants": [
    {
      "uuid": "550e8400-e29b-41d4-a716-446655440000",
      "tenantName": "acme",
      "establishmentName": "ACME Corp",
      "active": true,
      "activeSchoolYear": null,
      "upcomingSchoolYear": null,
      "schoolYears": []
    }
  ],
  "propertiesPerTenant": {
    "acme": {
      "ma.propriete.specifique": "valeur"
    }
  }
}
```

### Étape 5 — Configurer Liquibase

```yaml
spring:
  liquibase:
    enabled: true
    change-log: classpath:/db/changelog/mon-changelog.xml
    default-schema: public
```

### Étape 6 — Utiliser le contexte tenant

```java
@Service
@RequiredArgsConstructor
public class MonService {

    private final TenantContext tenantContext;

    public void faireQuelqueChose() {
        Tenant tenant = tenantContext.getCurrentTenant();
        String schema = tenantContext.getCurrentSchemaName();
        // JPA est déjà routé vers le bon schéma automatiquement
    }
}
```

### Étape 7 — Propriétés par tenant

```java
@TenantConfigurationProperties(prefix = "mon.module")
public class MonModuleProperties {
    private String apiUrl;
    private int timeout;
    // getters/setters
}
```

```java
@Service
@RequiredArgsConstructor
public class MonService {
    private final TenantConfigurationRegistry registry;

    public String getApiUrl() {
        return registry.get(MonModuleProperties.class).getApiUrl();
        // Retourne automatiquement la valeur pour le tenant courant
    }
}
```

### Étape 8 — Scheduling multi-tenant

```java
@Component
public class MaTache {

    @TenantScheduled(scheduled = @Scheduled(cron = "0 0 3 * * ?"))
    public void nettoyage() {
        // Exécuté une fois par tenant, TenantContext est positionné
    }
}
```

### Étape 9 — Cache tenant-aware

```java
@Cacheable(value = "maCache", keyGenerator = "multitenantKeyGenerator")
public MonObjet findById(Long id) {
    // Les données sont isolées par tenant dans le cache
}
```

---

## 7. Analyse critique — Couplages spécifiques à l'Espace Éducatif

> ⚠️ **Cette section est le cœur du travail de nettoyage à effectuer.**
>
> La librairie a été développée initialement (ou fortement enrichie) pour **une application d'Espace Éducatif** qui gère un cycle de vie d'**années scolaires** avec trois états de schéma : `_active`, `_upcoming` (à venir), `_archive_<code>`.
>
> Ces concepts métier sont **profondément couplés** au code de la librairie, la rendant non générique.

### 7.1 Inventaire complet des éléments à généraliser ou supprimer

#### Classes à **supprimer entièrement** (100% métier éducatif)

| Classe | Raison |
|---|---|
| `TenantSchoolYear` | Record métier pur : `begin`, `end`, `code`, `status` d'une année scolaire |
| `SchoolYearHistoryStatus` | Enum métier : `INITIALIZED`, `AWAITING`, `ACTIVE`, `ARCHIVED`, `CANCELLED` |
| `SchoolYearSchemaManager` | Gestion complète de la table `schoolyearactive`, de la promotion upcoming→active, de la restauration de données post-promotion |
| `StatefulMigrationOrchestrator` | Orchestration Saga de la migration d'une année scolaire à l'autre |

#### Classes à **refactorer significativement**

| Classe | Problème | Solution proposée |
|---|---|---|
| `Tenant.java` | Champ `schoolYear` — concept d'année scolaire | Renommer en `contextKey` (générique) ou supprimer. La notion de "contexte secondaire" peut être abstraite |
| `TenantContext.java` | Méthode `getCurrentYearCode()` retourne `schoolYear` | Renommer en `getCurrentContextKey()` ou supprimer si on supprime le champ |
| `TenantData.java` | Champs `establishmentName`, `activeSchoolYear`, `upcomingSchoolYear`, `schoolYears` — tous métier éducatif | Supprimer `activeSchoolYear`, `upcomingSchoolYear`, `schoolYears`. `establishmentName` peut devenir `displayName` (générique) |
| `SchemaUtils.java` | Suffixes `_active`, `_upcoming`, `_archive_` hardcodés — correspond au cycle de vie éducatif | À rendre configurable ou à supprimer si on supprime la feature de cycle de vie |
| `TenantConfigurationRegistry.java` | Méthode `getTenantSchoolYearBySchemaName()` dépendante de `SchoolYearHistoryStatus` | À supprimer avec le reste du cycle de vie |
| `TenantService.java` | Logique `manageSchoolYear`, calcul des schémas actifs/upcoming | À simplifier — retourner directement la liste des tenants sans logique d'année |
| `SchemaConfigurationProperties.java` | Flag `manageSchoolYear`, liste `excludeTablesForMigration` | `manageSchoolYear` à supprimer. `excludeTablesForMigration` peut rester mais être mieux nommé |
| `SchemaAdmin.java` | Référence à `schemaConfigurationProperties.isManageSchoolYear()` dans `getAllUpcomingSchemasNotExisting()` | À nettoyer |
| `TenantFilter.java` | Lecture et propagation du header `X-SCHOOL-YEAR` | Supprimer ce header ou le rendre optionnel/configurable |
| `TenantFeignInterceptor.java` | Propagation de `X-SCHOOL-YEAR` dans les appels Feign | Idem |
| `ProfileConstants.java` | Constantes `YEAR_SCHOOL` et `X_SCHOOL_YEAR` | À supprimer |
| `SwaggerUtils.java` | Génération des exemples basés sur `activeSchoolYear`, `upcomingSchoolYear`, `schoolYears` | À simplifier — exemples basés uniquement sur les noms de tenants |
| `TenantAppEventConfig.java` | Valeur par défaut `net.atos.digital.een` pour `multitenant.events.allowed-packages` | `een` = probable acronyme de l'Espace Éducatif National. À changer en liste vide ou package générique |
| `PublicSchemaLiquibaseConfig.java` | Hardcode `"classpath:/db/changelog/multitenant-changelog.xml"` et `"public"` | À externaliser via propriété |

---

#### Détail du couplage dans `TenantData.java`

```java
// AVANT (spécifique Espace Éducatif)
public record TenantData(
    UUID uuid,
    String tenantName,
    String establishmentName,  // ← "établissement" = concept éducatif
    boolean active,
    TenantSchoolYear activeSchoolYear,    // ← année scolaire active
    TenantSchoolYear upcomingSchoolYear,  // ← prochaine année scolaire
    List<TenantSchoolYear> schoolYears    // ← historique des années
) {}

// APRÈS (générique)
public record TenantData(
    UUID uuid,
    String tenantName,
    String displayName,   // nom affiché — générique
    boolean active
) {}
```

#### Détail du couplage dans `SchemaUtils.java`

Les suffixes de schéma `_active` / `_upcoming` / `_archive_<code>` sont **des conventions métier** de l'Espace Éducatif pour représenter les trois états d'un schéma dans le cycle de vie d'une année scolaire. **Une application générique n'a pas besoin de cette convention.**

```java
// AVANT (spécifique)
public final String SUFFIX_ACTIVE_SCHEMA = "_active";
public final String SUFFIX_UPCOMING_SCHEMA = "_upcoming";
public final String SUFFIX_ARCHIVE_SCHEMA = "_archive_";

// APRÈS (générique)
// Ces utilitaires n'ont plus de raison d'être dans la librairie générique.
// Seul quote() et les méthodes de base sont à conserver.
```

---

## 8. Recommandations de nettoyage pour généricisation

### Priorité 1 — Suppressions immédiates (aucun impact sur le comportement générique)

1. Supprimer `TenantSchoolYear.java`
2. Supprimer `SchoolYearHistoryStatus.java`
3. Supprimer `SchoolYearSchemaManager.java`
4. Supprimer `StatefulMigrationOrchestrator.java`
5. Supprimer la constante `X_SCHOOL_YEAR` et `YEAR_SCHOOL` de `ProfileConstants.java`
6. Supprimer le flag `manageSchoolYear` de `SchemaConfigurationProperties.java`
7. Changer la valeur par défaut de `multitenant.events.allowed-packages` de `net.atos.digital.een` vers `""` (liste vide)

### Priorité 2 — Refactoring du modèle `Tenant` / `TenantData`

1. Supprimer `schoolYear` de `Tenant.java` → le record devient `Tenant(UUID id, String name, String schemaName)`
2. Supprimer `getCurrentYearCode()` de `TenantContext.java`
3. Nettoyer `TenantData.java` → supprimer `activeSchoolYear`, `upcomingSchoolYear`, `schoolYears`, renommer `establishmentName` en `displayName`
4. Simplifier la méthode `toTenant()` dans `TenantData` (plus de logique d'année scolaire)
5. Supprimer `getTenantSchoolYearBySchemaName()` de `TenantConfigurationRegistry.java`

### Priorité 3 — Nettoyage des utilitaires

1. Simplifier `SchemaUtils.java` : supprimer `computeActiveSchemaName`, `computeUpcomingSchemaName`, `computeArchiveSchemaName`, `isActiveSchema`, `isUpcomingSchema`, `isArchivedSchema`, `isApplicableSchoolYear`. Ne garder que `quote()`
2. Supprimer la logique `X-SCHOOL-YEAR` de `TenantFilter.java`
3. Supprimer la propagation `X-SCHOOL-YEAR` de `TenantFeignInterceptor.java`
4. Simplifier `SwaggerUtils.java` (supprimer les exemples d'années scolaires)
5. Simplifier `TenantService.java`
6. Rendre le chemin du changelog Liquibase configurable dans `PublicSchemaLiquibaseConfig.java`

### Priorité 4 — Nettoyage de `SchemaAdmin.java`

1. Supprimer `getAllUpcomingSchemasNotExisting()` ou la généraliser sans référence à `manageSchoolYear`
2. Revoir la logique de `getAllUpcomingSchemasNotExisting()` qui n'a de sens qu'avec le concept d'année scolaire

### Priorité 5 — Points à revoir (provisioning de schéma)

1. **Vérifier si le schema existe déjà avant de le (re)créer** : `DatabaseSchemaInitializer.createTenantsSchemas()` et `NewTenantHandler` exécutent `CREATE SCHEMA IF NOT EXISTS` à chaque refresh, même si le schema existe déjà. Ajouter une vérification préalable (`SELECT schema_name FROM information_schema.schemata WHERE schema_name = ?`) pour éviter les logs/opérations inutiles.
2. **Conflit de provisioning synchrone vs asynchrone** : quand une application consommatrice provisionne un schema de manière synchrone (lors de la création d'une organisation par ex.), le `NewTenantHandler` retente de créer le même schema au prochain cycle de refresh. Le `IF NOT EXISTS` rend cela idempotent mais ce n'est pas optimal — envisager un mécanisme de marquage « schema déjà provisionné » pour éviter les tentatives redondantes.
3. **Case-sensitivity des noms de schemas** : `DatabaseSchemaInitializer` utilise des identifiants quotés (`"schemaName"`) ce qui rend les noms case-sensitive dans PostgreSQL. Normaliser en lowercase dans `TenantData.toTenant()` (fix appliqué) mais aussi envisager de supprimer le quoting dans le `CREATE SCHEMA` ou de systématiquement lowercaser dans `DatabaseSchemaInitializer`.

### Ce qu'il faut **conserver tel quel** (déjà générique)

- `TenantContext` (sans `getCurrentYearCode`)
- `TenantFilter` (sans la logique `X-SCHOOL-YEAR`)
- `TenantJpaConfig` / `SchemaBasedMultiTenantConnectionProvider`
- `TenantKafkaConfig` / `TenantJmsConfig`
- `TenantFeignInterceptor` (sans `X-SCHOOL-YEAR`)
- `MultitenantKeyGenerator` / `TenantCacheConfig`
- `TenantScheduled` / `TenantScheduledAnnotationBeanPostProcessor`
- `JdbcTableDistributedLockManager` / `DistributedLockManager`
- `ShedlockJpaConfig`
- `TenantConfigurationRegistrar` / `TenantConfigurationRegistry`
- `LiquibaseReadinessChecker`
- `TenantLiquibaseConfig`
- `TenantAppEventConfig` (après correction de la valeur par défaut)
- `@EnableSidebar` / `SidebarConfiguration`
- `@TenantConfigurationProperties`

---

## 9. Dépendances

| Dépendance | Scope | Rôle |
|---|---|---|
| `spring-boot-starter-data-jpa` | provided | JPA / Hibernate |
| `spring-boot-starter-web` | provided | Filtre HTTP servlet |
| `spring-boot-starter-activemq` | provided | JMS / ActiveMQ |
| `spring-kafka` | provided | Kafka |
| `spring-cloud-starter-openfeign` | provided | Intercepteur Feign |
| `spring-cloud-config-client` | provided | Lecture du Config Server |
| `spring-boot-starter-cache` | provided | Génération de clés de cache |
| `liquibase-core` | provided | Migration des schémas |
| `springdoc-openapi-starter-webmvc-ui` | provided | Swagger / SpringDoc |
| `spring-boot-starter-quartz` | provided | Scheduling |
| `shedlock-spring` + `shedlock-provider-jdbc-template` | compile | ShedLock |
| `reflections` | compile | Scan des classes `@TenantConfigurationProperties` |
| `digital-tmp-messagebroker-appevent` | compile | API `EventNotifier` |
| `postgresql` | provided | Driver PostgreSQL |
| `lombok` | optional | Réduction du boilerplate |

> Toutes les dépendances en scope `provided` doivent être présentes dans l'application cliente.

---

## 10. Build & Publication

### Build local

```bash
mvn clean install
```

### Publication sur GitHub Packages

```bash
mvn deploy
```

Les artefacts sont publiés sur :
```
https://maven.pkg.github.com/sn-dkr-dig-tmp/digital-tmp-multitenant
```

### Consommer depuis GitHub Packages

Ajouter dans le `pom.xml` de l'application cliente :

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/sn-dkr-dig-tmp/digital-tmp-multitenant</url>
    </repository>
</repositories>
```

Et dans `~/.m2/settings.xml` :

```xml
<server>
    <id>github</id>
    <username>VOTRE_USERNAME_GITHUB</username>
    <password>VOTRE_TOKEN_GITHUB</password>
</server>
```

---

*Document rédigé le 22/05/2026 — Analyse complète du code source de la version `1.0.0-SNAPSHOT`.*