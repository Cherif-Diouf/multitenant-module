# digital-tmp-multitenant

**Rendez votre application Spring Boot multi-tenant en quelques minutes, sans toucher à votre code métier.**

Cette librairie isole les données de vos tenants (clients, organisations, agences…) par **un schéma PostgreSQL par tenant**, et propage le contexte tenant de façon transparente à travers toutes les couches de l'application : HTTP, JPA/Hibernate, `@Async`, Kafka, JMS, Feign, cache, tâches planifiées, événements Spring et verrous distribués.

Votre code métier écrit `documentRepository.findAll()` — la librairie s'assure que la requête s'exécute dans le schéma du bon tenant. C'est tout.

---

## Sommaire

1. [Comment ça marche — en 30 secondes](#1-comment-ça-marche--en-30-secondes)
2. [Démarrage rapide](#2-démarrage-rapide)
3. [Concepts clés](#3-concepts-clés)
4. [Résolution du tenant HTTP : mode `legacy` et mode `resolver`](#4-résolution-du-tenant-http)
5. [Propagation automatique du contexte](#5-propagation-automatique-du-contexte)
6. [Exécuter du code pour un ou plusieurs tenants : `TenantExecutor`](#6-tenantexecutor--exécution-cross-tenant)
7. [Cycle de vie des tenants : provisioning et dé-provisioning](#7-cycle-de-vie-des-tenants)
8. [Configuration par tenant : `@TenantConfigurationProperties`](#8-configuration-par-tenant)
9. [Référence des propriétés](#9-référence-des-propriétés)
10. [Pièges courants et FAQ](#10-pièges-courants-et-faq)

---

## 1. Comment ça marche — en 30 secondes

```
Requête HTTP entrante
        │
        ▼
┌─────────────────────────┐     "à quel tenant appartient cette requête ?"
│  Filtres de la librairie │ ──► header X-TENANT, claim JWT, sous-domaine…
└───────────┬─────────────┘
            ▼
   TenantContext (ThreadLocal)          ◄── le cœur du système
            │
            ├──► JPA/Hibernate  ──► SET search_path TO 'tenant_a'   (un schéma par tenant)
            ├──► Cache          ──► clés préfixées par le tenant
            ├──► @Async         ──► contexte recopié sur le thread du pool
            ├──► Kafka / JMS    ──► tenant transporté dans les en-têtes de message
            ├──► Feign          ──► header X-TENANT ajouté aux appels sortants
            └──► @TenantScheduled ─► la tâche s'exécute une fois PAR tenant
```

- **Isolation forte** : chaque tenant a son propre schéma PostgreSQL. Sauvegarder, archiver ou
  supprimer un tenant = manipuler un schéma.
- **Transparence** : le contexte est posé aux points d'entrée (HTTP, message, tâche planifiée)
  et nettoyé automatiquement en fin de traitement. Les repositories, services et contrôleurs
  n'ont **aucune** connaissance du multi-tenant.
- **Extensible** : la façon d'identifier le tenant d'une requête est un point d'extension (SPI).
  La librairie ne fait aucune hypothèse sur votre fournisseur d'identité.

---

## 2. Démarrage rapide

### Étape 1 — Ajouter la dépendance

```xml
<dependency>
    <groupId>net.atos.digital</groupId>
    <artifactId>multitenant</artifactId>
    <version>${digital-tmp-multitenant.version}</version>
</dependency>
```

### Étape 2 — Activer la librairie

```java
@SpringBootApplication
@EnableSidebar          // active le scan des composants de la librairie
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

Puis démarrer l'application avec les bons **profils Spring** :

```bash
-Dspring.profiles.active=multiSchema,multitenant
```

| Profil | Rôle |
|---|---|
| `multiSchema` | Active le routage multi-schéma (Hibernate, Liquibase, scheduling par tenant…) |
| `multitenant` | Active les tenants **dynamiques** (registre alimenté par le Config Manager, provisioning à chaud) |

> Pour un démarrage sans Config Manager (dev local, liste de tenants figée), le profil
> `multiSchema` seul suffit : les tenants sont alors ceux déclarés dans `schema-configuration.schemas`.

### Étape 3 — Configurer les schémas

```yaml
schema-configuration:
  multiple-schema: true

  default-schema:
    schema-name: "public"
    contexts: ["default"]
    liquibase-enabled: false
    exclude-paths-filter: []

  # Tenants statiques (optionnel — en mode multitenant, le registre
  # du Config Manager alimente la liste dynamiquement)
  schemas:
    - schema-name: "tenant_a"
      contexts: ["tenant_a"]
      liquibase-enabled: true
      exclude-paths-filter: []

  # Chemins HTTP qui ne nécessitent pas de tenant (login, pages publiques…)
  filters:
    exclude-paths-filter:
      - "/auth/**"
```

Les exclusions techniques (`/actuator/**`, swagger, api-docs) sont ajoutées automatiquement.

### Étape 4 — (mode `multitenant`) Brancher le Config Manager

Le registre des tenants est chargé au démarrage depuis un serveur de configuration,
puis rafraîchi périodiquement :

```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8090/api/config/v1

multitenant:
  config-manager:
    base-url: http://localhost:8090
    auto-create-application: true   # crée l'application côté Config Manager si absente
    fail-fast: false                # true = échec au démarrage si Config Manager indisponible
```

### Étape 5 — Appeler votre API

```bash
curl http://localhost:8080/api/documents -H "X-TENANT: tenant_a"
```

Toutes les requêtes JPA de ce traitement s'exécutent dans le schéma `tenant_a`. C'est terminé
pour le cas nominal — la suite de ce document couvre les canaux asynchrones, la résolution
personnalisée du tenant (JWT…) et le cycle de vie des tenants.

---

## 3. Concepts clés

### Tenant

Une entité isolée de l'application (un client, une organisation…), identifiée par un UUID,
un **nom** (celui que transportent les headers et les messages) et un **nom de schéma**
PostgreSQL (le nom en minuscules — les schémas sont toujours lowercase, `^[0-9a-z_]+$`).

### TenantContext

Le cœur du système : un conteneur `ThreadLocal` qui détient le tenant courant pendant toute
la durée d'un traitement. Il est posé aux points d'entrée (filtre HTTP, consommateur de message,
tâche planifiée) et **systématiquement nettoyé en `finally`** pour éviter qu'un tenant ne
« colle » à un thread de pool.

Règle d'or : **le code applicatif ne manipule jamais `TenantContext` directement.**
Pour exécuter du code au nom d'un tenant précis, utilisez [`TenantExecutor`](#6-tenantexecutor--exécution-cross-tenant).

### TenantConfigurationRegistry

Le registre en mémoire de tous les tenants connus et de leurs propriétés. En mode `multitenant`
il est peuplé depuis le Config Manager (au démarrage puis par rafraîchissement périodique) ;
en mode `multiSchema` seul, il reflète les schémas statiques de la configuration.

### Cycle de vie d'une requête HTTP

```
1. Filtre(s) de la librairie : identification du tenant  →  TenantContext posé
2. Spring Security : authentification (les accès BDD utilisent déjà le bon schéma)
3. Contrôleur → services → repositories : transparents
4. Fin de requête : TenantContext nettoyé (finally), même en cas d'exception
```

---

## 4. Résolution du tenant HTTP

**Comment la librairie sait-elle à quel tenant appartient une requête ?**
Deux modes, pilotés par `multitenant.filter.mode` :

### Mode `legacy` (défaut) — header `X-TENANT`

Un filtre unique (`TenantFilter`) lit le header `X-TENANT`, vérifie que le tenant existe
(registre ou schémas configurés) et pose le contexte. Requête sans header sur un chemin
non exclu → **400**. Simple et suffisant quand vos clients envoient toujours le header
(gateway, front dédié, appels M2M).

### Mode `resolver` — SPI extensible (recommandé)

À activer dès que le tenant ne peut pas être déduit d'un simple header : claim JWT,
sous-domaine, segment d'URL, certificat client… **et** dès que vous avez besoin de règles
d'autorisation (« cet appelant a-t-il le droit d'opérer sur ce tenant ? »).

```yaml
multitenant:
  filter:
    mode: resolver
```

La librairie enregistre alors **deux filtres** :

| Filtre | Ordre servlet | Rôle |
|---|---|---|
| `TenantResolutionFilter` | `-200` (avant Spring Security) | Pose le contexte en *best-effort*, sans jamais rejeter. Indispensable si votre couche sécurité fait des requêtes BDD **pendant** l'authentification (résolution de permissions par rôle, chargement d'utilisateur) : ces requêtes ciblent ainsi déjà le bon schéma. |
| `TenantEnforcementFilter` | `10` (après Spring Security) | Ré-identifie le tenant avec le `SecurityContext` peuplé, applique votre politique d'accès, vérifie l'existence du tenant (sinon **403**), pose le contexte. Nettoyage garanti en `finally`. |

Ces deux filtres délèguent à deux SPI que **vous** implémentez (ou pas — des défauts existent) :

#### SPI n° 1 : `TenantResolver` — « quel est le tenant de cette requête ? »

Tous les beans `TenantResolver` du contexte sont interrogés **par ordre croissant**
(`getOrder()`) ; le premier résultat non vide gagne.

La librairie fournit un seul résolveur : `HeaderTenantResolver` (header `X-TENANT`,
normalisé en minuscules, ordre `-100` — donc prioritaire). Elle est **agnostique du
fournisseur d'identité** : si votre tenant vit dans un JWT, c'est vous qui l'exprimez.

```java
/**
 * Exemple : résout le tenant depuis un claim JWT.
 * Attention : ce résolveur peut être appelé AVANT Spring Security (filtre pré-auth).
 * Il ne doit donc pas supposer que le SecurityContext est peuplé — dans ce cas,
 * décoder le payload du JWT sans valider la signature est acceptable : la signature
 * sera validée par Spring Security juste après, et le filtre post-auth re-validera tout.
 */
@Component
public class MyJwtTenantResolver implements TenantResolver {

    @Override
    public Optional<String> resolveTenantName(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            // Post-authentification : claim validé
            return Optional.ofNullable(jwtAuth.getToken().getClaimAsString("tenant"));
        }
        // Pré-authentification : décodage best-effort du payload brut
        return decodeTenantFromRawBearer(request);
    }

    @Override
    public int getOrder() {
        return 0; // après HeaderTenantResolver (-100) : un header explicite gagne toujours
    }
}
```

Contrat d'un résolveur : retourner le **nom du tenant** (pas le schéma), `Optional.empty()`
si la stratégie ne s'applique pas, et **ne jamais lever d'exception** pour un simple échec
de résolution.

#### SPI n° 2 : `TenantAccessValidator` — « cet appelant a-t-il le droit ? »

Appelé une seule fois par requête, **après** authentification, avec le tenant résolu
(ou `null`). Trois décisions possibles :

```java
@Component
public class MyTenantAccessValidator implements TenantAccessValidator {

    @Override
    public TenantAccessDecision validate(HttpServletRequest request, String tenantName) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (isPlatformAdmin(auth)) {
            // Un admin plateforme doit cibler explicitement un tenant (X-TENANT)
            return tenantName != null
                    ? TenantAccessDecision.proceed()
                    : TenantAccessDecision.reject(403, "Header X-TENANT requis");
        }
        if (auth != null && !belongsTo(auth, tenantName)) {
            return TenantAccessDecision.reject(403, "Tenant non autorisé pour cet utilisateur");
        }
        if (tenantName == null) {
            // Endpoint public : on laisse passer sans tenant
            return TenantAccessDecision.proceedWithoutTenant();
        }
        return TenantAccessDecision.proceed();
    }
}
```

Sans bean applicatif, le défaut (`RequireTenantAccessValidator`) exige un tenant et rejette
en 400 — le même contrat que le mode `legacy`.

#### Configuration du mode `resolver`

```yaml
multitenant:
  filter:
    mode: resolver
    pre-auth-enabled: true          # filtre best-effort avant Spring Security
    pre-auth-order: -200
    pre-auth-exclude-paths:         # chemins ignorés par le filtre pré-auth
      - "/platform/**"              #   (ex. endpoints cross-tenant d'administration)
    enforcement-order: 10
    header-resolver-enabled: true   # false pour désactiver le résolveur X-TENANT fourni

schema-configuration:
  filters:
    exclude-paths-filter:           # chemins exclus du filtre d'ENFORCEMENT
      - "/auth/**"                  #   (les exclusions actuator/swagger sont automatiques)
      - "/public/**"
```

> Les patterns sont des AntPath évalués contre le **servletPath** (sans le context-path).

#### Migration `legacy` → `resolver`

Le mode `legacy` reste le défaut : les applications existantes ne changent rien.
Pour migrer : passer `multitenant.filter.mode=resolver` — sans autre changement, le
comportement est identique au legacy (résolveur header + validateur « tenant obligatoire »).
Ajoutez ensuite vos propres résolveur(s)/validateur au fil des besoins.

---

## 5. Propagation automatique du contexte

Une fois le contexte posé, la librairie le transporte sur tous les canaux. **Aucun code
applicatif n'est nécessaire** — cette section documente ce qui se passe sous le capot.

| Canal | Mécanisme | Composant |
|---|---|---|
| JPA / Hibernate | `SET search_path` par connexion (stratégie SCHEMA) | `SchemaBasedMultiTenantConnectionProvider` |
| `@Async`, Spring Modulith | `TaskDecorator` qui recopie le contexte sur le thread du pool | `TenantAsyncConfig` |
| Kafka | En-tête de message `tenant` (production ET consommation) | `MultitenantKafkaMessageConverter`, `TenantKafkaRecordInterceptor` |
| JMS | Propriété de message `tenant` | `TenantMappingJackson2MessageConverter` |
| Feign (appels sortants) | Header `X-TENANT` ajouté automatiquement | `TenantFeignInterceptor` |
| Cache Spring | Clés préfixées par le tenant | `multitenantKeyGenerator` |
| Événements Spring | Wrapping de l'événement avec son tenant | `MultiTenantEventPublisherProxy`, `MultitenantEventDispatcher` |
| Tâches planifiées | Une exécution **par tenant**, contexte posé | `@TenantScheduled` |
| Verrous distribués | Table ShedLock **dans le schéma du tenant** | `ShedlockJpaConfig` |

Quelques points d'attention par canal :

### `@Async`

`TenantAsyncConfig` fournit l'`AsyncConfigurer` (threads `tenant-async-*`). Si vous déclarez
votre propre `AsyncConfigurer`, celui de la librairie s'efface (`@ConditionalOnMissingBean`) —
pensez alors à recopier le contexte vous-même via un `TaskDecorator`.

### Kafka

Le nettoyage du contexte après **chaque** message (succès ou échec) est garanti par
`TenantKafkaRecordInterceptor` — indispensable avec les pools de threads des consommateurs.

### Cache

Utilisez le générateur fourni dans vos annotations :

```java
@Cacheable(value = "documents", keyGenerator = "multitenantKeyGenerator")
public Document find(String id) { ... }
```

### Tâches planifiées

`@TenantScheduled` remplace `@Scheduled` : la méthode s'exécute une fois **par tenant**,
avec le contexte posé. Combinez avec `@SchedulerLock` (ShedLock) pour garantir une seule
exécution par tenant en environnement multi-instances :

```java
@TenantScheduled(scheduled = @Scheduled(fixedRate = 60000))
@SchedulerLock(name = "processData", lockAtMostFor = "50s")
public void processDataForAllTenants() {
    // exécutée pour chaque tenant, dans son schéma, verrou isolé par schéma
}
```

```yaml
jpa:
  shedlock:
    enabled: true          # active ShedLock multi-tenant

schema-configuration:
  include-default-schema-in-scheduling: false   # exclure le schéma par défaut des exécutions
```

### Événements Spring

Les événements publiés pendant un traitement tenant-scopé sont automatiquement enveloppés
avec leur tenant et dé-enveloppés côté listener — y compris à travers `@Async`.

### Physical naming strategy (Hibernate)

La librairie ne l'impose pas (`putIfAbsent`) : déclarez la vôtre si votre base est en snake_case :

```yaml
spring:
  jpa:
    properties:
      hibernate:
        physical_naming_strategy: org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
```

---

## 6. `TenantExecutor` — exécution cross-tenant

Pour les traitements qui doivent s'exécuter **au nom d'un tenant précis** ou **pour tous les
tenants** (agrégations d'administration, batchs, migrations, compensation), injectez
`TenantExecutor` — jamais `TenantContext` :

```java
@Service
@RequiredArgsConstructor
public class PlatformStatsService {

    private final TenantExecutor tenantExecutor;
    private final DocumentRepository documentRepository;

    /** Agréger une valeur sur tous les tenants. */
    public long countAllDocuments() {
        AtomicLong total = new AtomicLong();
        tenantExecutor.forEachTenant(tenant ->
                total.addAndGet(documentRepository.count()));
        return total.get();
    }

    /** Exécuter une lecture dans un tenant donné (par nom). */
    public DocumentDto findInTenant(String tenantName, String id) {
        return tenantExecutor.callAs(tenantName, () -> documentService.get(id));
    }
}
```

| Méthode | Usage |
|---|---|
| `runAs(tenant, Runnable)` / `runAs(tenantName, Runnable)` | Exécuter une action au nom d'un tenant |
| `callAs(tenant, Supplier<T>)` / `callAs(tenantName, Supplier<T>)` | Idem, avec valeur de retour |
| `forEachTenant(Consumer<Tenant>)` | Itérer sur tous les tenants disponibles |
| `forEachSchedulableTenant(Consumer<Tenant>)` | Idem, en respectant `include-default-schema-in-scheduling` |

Garanties — c'est ce qui rend `TenantExecutor` supérieur à toute manipulation manuelle du contexte :

- le **contexte précédent est restauré** en sortie (un appel imbriqué ou une itération
  au sein d'une requête tenant-scopée ne perd pas le contexte de l'appelant) ;
- la restauration passe par un `finally` : elle a lieu **même en cas d'exception** ;
- `forEachTenant` **continue sur les tenants suivants** si l'un d'eux échoue (erreur loggée).

---

## 7. Cycle de vie des tenants

### Provisionner un tenant à chaud

`TenantProvisioningService.provision(tenantId, tenantName)` crée un tenant **pendant que
l'application tourne** :

1. validation du nom de schéma (`^[0-9a-z_]+$`) ;
2. `CREATE SCHEMA IF NOT EXISTS` (hors transaction — le DDL doit être visible immédiatement) ;
3. exécution des migrations **Liquibase** sur le nouveau schéma ;
4. enregistrement dans le `TenantConfigurationRegistry` ;
5. positionnement du `TenantContext` sur le nouveau tenant, pour que vous puissiez
   immédiatement créer les données initiales (utilisateur admin, paramètres…).

```java
@Service
@RequiredArgsConstructor
public class CustomerOnboardingService {

    private final ConfigManagerClient configManagerClient;
    private final TenantProvisioningService provisioningService;

    public void onboard(String code, String displayName) {
        String tenantId = null;
        boolean schemaProvisioned = false;
        try {
            // 1. Déclarer le tenant au Config Manager (create-or-get : idempotent)
            tenantId = configManagerClient.createTenant(code, displayName);

            // 2. Schéma + Liquibase + registre — le contexte est posé sur le nouveau tenant
            provisioningService.provision(tenantId, code);
            schemaProvisioned = true;

            // 3. Données initiales, DANS le nouveau schéma
            userRepository.save(newAdminUser());

        } catch (Exception ex) {
            // Compensation : ne détruire que ce qui a été créé
            if (schemaProvisioned) provisioningService.deprovision(code);
            if (tenantId != null) configManagerClient.deleteTenant(tenantId);
            throw ex;
        } finally {
            provisioningService.clearContext();   // toujours, en finally
        }
    }
}
```

### Dé-provisionner un tenant

`deprovision(tenantName)` est l'opération symétrique : retrait du registre local puis
`DROP SCHEMA … CASCADE`. **Destructrice et définitive** — réservez-la à la suppression
d'un tenant ou à la compensation d'un provisioning échoué. Le désenregistrement côté
Config Manager reste un appel explicite (`configManagerClient.deleteTenant(id)`), car un
tenant peut exister d'un côté sans exister de l'autre.

### API Config Manager (`ConfigManagerClient`)

En plus de la lecture du registre (automatique), le client expose le cycle de vie complet :

| Méthode | Rôle |
|---|---|
| `createTenant(name, displayName)` | Crée le tenant — *create-or-get* : retourne l'id existant sur 409 |
| `listTenants()` / `findTenantIdByName(name)` | Liste / recherche les tenants de l'application |
| `getTenantProperties(id)` / `updateTenantProperties(id, map)` | Lecture / remplacement des propriétés d'un tenant |
| `deleteTenant(id)` | Supprime le tenant côté Config Manager |

---

## 8. Configuration par tenant

Pour les paramètres qui varient d'un tenant à l'autre (quotas, feature flags, plan commercial…),
remplacez `@ConfigurationProperties` par `@TenantConfigurationProperties` :

```java
@Data
@TenantConfigurationProperties(prefix = "billing")
public class BillingTenantProperties {
    private String plan = "STARTER";
    private int maxUsers = 10;
    private boolean premiumFeaturesEnabled = false;
}
```

```java
// Déclarer le bean prototype — la librairie instancie et peuple UNE copie par tenant
@Bean
public BillingTenantProperties billingTenantProperties() {
    return new BillingTenantProperties();
}
```

Les valeurs proviennent des propriétés du tenant sur le Config Manager
(`billing.plan=BUSINESS`, `billing.max-users=50`, …).

**Ne jamais injecter ces beans directement** (vous obtiendriez une instance arbitraire) —
passez par le registre, qui sélectionne l'instance du tenant courant :

```java
@RequiredArgsConstructor
public class QuotaService {
    private final TenantConfigurationRegistry registry;

    public void checkQuota() {
        BillingTenantProperties props = registry.get(BillingTenantProperties.class);
        if (userRepository.count() >= props.getMaxUsers()) {
            throw new QuotaExceededException();
        }
    }
}
```

---

## 9. Référence des propriétés

### `schema-configuration.*` — schémas et filtrage

| Propriété | Défaut | Description |
|---|---|---|
| `multiple-schema` | — | Active le mode multi-schéma |
| `default-schema.schema-name` | `public` | Schéma par défaut (requêtes hors tenant) |
| `default-schema.liquibase-enabled` | `true` | Migrations Liquibase sur le schéma par défaut |
| `schemas[]` | `[]` | Tenants statiques : `schema-name`, `contexts`, `liquibase-enabled`, `exclude-paths-filter` |
| `filters.exclude-paths-filter` | `[]` | Chemins HTTP exclus du filtrage tenant (AntPath sur servletPath) |
| `include-default-schema-in-scheduling` | `true` | Inclure le schéma par défaut dans `@TenantScheduled` / `forEachSchedulableTenant` |
| `exclude-tables-for-migration` | changelog Liquibase | Tables exclues des migrations |

### `multitenant.filter.*` — résolution HTTP

| Propriété | Défaut | Description |
|---|---|---|
| `mode` | `legacy` | `legacy` (X-TENANT seul) ou `resolver` (SPI) |
| `pre-auth-enabled` | `true` | Filtre best-effort avant Spring Security (mode resolver) |
| `pre-auth-order` | `-200` | Ordre servlet du filtre pré-auth |
| `pre-auth-exclude-paths` | `[]` | Chemins ignorés par le filtre pré-auth |
| `enforcement-order` | `10` | Ordre servlet du filtre d'enforcement |
| `header-resolver-enabled` | `true` | Active le résolveur `X-TENANT` fourni |

### `multitenant.config-manager.*` — registre des tenants

| Propriété | Défaut | Description |
|---|---|---|
| `base-url` | déduit de `spring.config.import` | URL du Config Manager |
| `registry-path` | `/api/config/v1/registry/{applicationName}` | Endpoint de lecture du registre |
| `application-path` | `/api/config/v1/applications` | Endpoint de création d'application / de tenants |
| `tenants-path` | `/api/config/v1/tenants` | Endpoint des propriétés de tenant |
| `auto-create-application` | `true` | Crée l'application si le registre renvoie 404 |
| `fail-fast` | `true` | Échec du démarrage si le Config Manager est injoignable |

### Divers

| Propriété | Défaut | Description |
|---|---|---|
| `jpa.shedlock.enabled` | `false` | Active ShedLock multi-tenant (verrous par schéma) |
| `spring.profiles.active` | — | `multiSchema` et/ou `multitenant` (cf. [Démarrage rapide](#2-démarrage-rapide)) |

---

## 10. Pièges courants et FAQ

**Mon code sécurité fait des requêtes BDD pendant l'authentification et tape le mauvais schéma.**
C'est le cas d'usage du filtre pré-auth du mode `resolver` : il pose le contexte à l'ordre
`-200`, avant Spring Security (`-100`). Vérifiez que votre `TenantResolver` sait résoudre
sans `SecurityContext` (décodage best-effort du JWT brut, header, sous-domaine…).

**Un utilisateur reçoit 400/403 sur un endpoint qui ne devrait pas exiger de tenant.**
Ajoutez le chemin à `schema-configuration.filters.exclude-paths-filter`. Rappel : les patterns
s'évaluent sur le **servletPath**, sans le context-path (`/auth/**`, pas `/api/v1/auth/**`).

**Les noms de tenant et de schéma.**
Le nom du schéma est toujours le nom du tenant en minuscules, limité à `[0-9a-z_]`.
Normalisez côté résolveur (le `HeaderTenantResolver` fourni le fait déjà).

**Puis-je poser le `TenantContext` moi-même ?**
Évitez. `TenantExecutor` fait la même chose avec la restauration du contexte précédent et
le nettoyage en `finally`. Un `setCurrentTenant` sans `clear` dans un pool de threads est
la première cause de fuite de données entre tenants.

**`InheritableThreadLocal` et threads créés à la main.**
Le contexte se propage aux threads enfants créés directement (`new Thread()`). Pour les
**pools** (`@Async`, Kafka, schedulers), la propagation est assurée par les composants de la
librairie — mais un `ExecutorService` maison créé hors de ces mécanismes ne recevra pas le
contexte : passez par `TenantExecutor.runAs(...)` dans la tâche soumise.

**Un tenant créé à chaud n'apparaît pas après redémarrage.**
Le registre en mémoire est reconstruit au démarrage depuis le Config Manager : le tenant doit
y avoir été déclaré (`ConfigManagerClient.createTenant`) en plus du provisioning du schéma.

**Liquibase : que ne doit PAS contenir mon changelog ?**
Aucun `schemaName` en dur — la librairie exécute le même changelog sur chaque schéma en
positionnant `currentSchema` sur la connexion.

**Comment déboguer le routage de schéma ?**
Passez ces loggers en `TRACE` :
```yaml
logging:
  level:
    net.atos.digital.multitenant: DEBUG
    net.atos.digital.multitenant.tenant.hibernate.SchemaBasedMultiTenantConnectionProvider: TRACE
```
