# Bibliothèque de Multi-Tenancy Avancée pour Projets Spring

Cette bibliothèque a pour objectif d'intégrer une gestion multi-tenant robuste et transparente au sein d'une application Spring. Elle est conçue pour isoler complètement les données de différents tenants (clients, organisations, espaces, etc.) sans nécessiter beaucoup de modifications du code métier.

Elle prend en charge la multi-tenancy pour :
- **Bases de données relationnelles** (JPA/Hibernate)
- **Provisioning de tenant à chaud** (CREATE SCHEMA + Liquibase à runtime)
- **Méthodes asynchrones** (`@Async`, Spring Modulith `@ApplicationModuleListener`)
- **Messagerie Asynchrone** (Kafka)
- **Messagerie JMS** (Java Message Service)
- **Appels inter-services** (Feign)
- **Cache applicatif** (Spring Cache)
- **Tâches planifiées** (Spring Scheduling)
- **Événements d'application** (Spring Application Events)
- **Verrouillage distribué** (ShedLock)

L'isolation est garantie par séparation physique des données (un schéma par tenant) et propagation transparente du contexte tenant à travers tous les couches de l'application.

## Principe de Fonctionnement

L'architecture repose sur une séparation physique des données et une propagation transparente du contexte pour chaque tenant :

- **Base de données** : **Un schéma par tenant**. Les données sont complètement isolées au niveau du schéma PostgreSQL.
- **Kafka** : Le contexte du tenant est propagé via les **en-têtes de message**.
- **JMS** : Le contexte tenant est transmis via les **propriétés de message** (`tenant`).
- **API REST/Feign** : Propagation via les **en-têtes HTTP** (`X-TENANT`).
- **Cache** : Isolation via le **préfixage automatique** des clés de cache avec l'identifiant tenant.
- **Tâches planifiées** : Exécution **par tenant** avec contexte automatiquement défini.
- **Événements d'application** : **Wrapping automatique** des événements avec le contexte tenant et dispatch via proxy.
- **Verrouillage distribué** : Verrous **isolés par schéma** tenant pour ShedLock.

Cette approche garantit une isolation forte des données, simplifiant des opérations comme la sauvegarde, l'archivage ou la suppression d'un tenant.


## Concepts Clés

### Tenant
Le `Tenant` représente une entité isolée au sein de l'application (un client, une organisation, une division, etc.). Il est identifié par un UUID unique et un nom lisible. Chaque tenant possède son propre schéma de base de données pour garantir l'isolation complète des données.

### TenantContext
C'est le cœur du système. Cet objet, stocké dans un `InheritableThreadLocal`, détient le `Tenant` courant pour toute la durée du traitement d'une requête ou d'un message. Il est systématiquement initialisé aux points d'entrée de l'application et nettoyé à la fin du traitement pour éviter les fuites de contexte.

> **Note** : `InheritableThreadLocal` propage le contexte aux threads enfants créés directement (`new Thread()`). Pour les **thread pools** (utilisés par `@Async`, Kafka consumers, etc.), la propagation est assurée par les composants dédiés de la bibliothèque (`TenantAsyncConfig`, `TenantKafkaRecordInterceptor`, etc.).

### TenantConfigurationRegistry
Ce bean centralise la configuration de tous les tenants. Il est peuplé au démarrage en contactant un serveur de configuration et contient les instances des beans de propriétés spécifiques à chaque tenant.

## Mise en Place et Configuration

### 1. Activation
La bibliothèque est activée en utilisant les profils Spring `multiSchema` et/ou `multitenant`.

#### Démarrage d'un module intégrant la bibliothèque
Les modules qui intègrent cette bibliothèque doivent être démarrés avec les paramètres JVM suivants :

```bash
-Dserver.servlet.context-path=/app/
-Dserver.port=8080
-Dspring.config.import=optional:configserver:http://localhost:8090/api/config/v1
-Dspring.cloud.config.uri=http://localhost:8090/api/config/v1
-Dspring.profiles.active=multiSchema,multitenant
```

**Paramètres essentiels :**
- `spring.profiles.active` :
  - `multiSchema` : Active la gestion de schémas multiples
  - `multitenant` : Active les fonctionnalités multi-tenant (propagation de contexte, etc.)
- `spring.config.import` : URL du Configuration Manager (préfixe `optional:` pour ne pas bloquer au démarrage si indisponible)
- `spring.cloud.config.uri` : Même URL, utilisée par Spring Cloud Config Client

Vous pouvez également utiliser l'annotation `@EnableSidebar` sur votre classe de configuration principale pour activer automatiquement toutes les fonctionnalités :

```java
@SpringBootApplication
@EnableSidebar
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

Cette annotation active automatiquement :
- Le scan des composants de la bibliothèque
- Le scan des propriétés de configuration
- Toutes les fonctionnalités multi-tenant

### 2. Configuration des Propriétés

#### Configuration Globale (`SchemaConfigurationProperties`)
Les modules qui intègrent cette bibliothèque doivent définir les propriétés de configuration suivantes :

```yaml
schema-configuration:
  multiple-schema: true
  default-schema:
    schema-name: "default_schema"
    contexts: ["default"]
    liquibase-enabled: true
    exclude-paths-filter: []
  include-default-schema-in-scheduling: true
  schemas:
    - schema-name: "tenant_a"
      contexts: ["tenant_a"]
      liquibase-enabled: true
      exclude-paths-filter: ["/actuator", "/swagger"]
    - schema-name: "tenant_b"
      contexts: ["tenant_b"]
      liquibase-enabled: true
      exclude-paths-filter: []
  exclude-tables-for-migration:
    - "databasechangelog"
    - "databasechangeloglock"
```

**Propriétés principales :**
- `multiple-schema` : Active le mode multi-schéma
- `schemas` : Liste des configurations de schémas avec leurs contextes
- `default-schema` : Configuration du schéma par défaut
- `include-default-schema-in-scheduling` : Inclut le schéma par défaut dans l'exécution des tâches planifiées `@TenantScheduled` (par défaut `true`). Mettre à `false` pour que les tâches planifiées ne s'exécutent que sur les schémas tenant réels.
- `exclude-tables-for-migration` : Tables à exclure lors des migrations

#### Configuration Spécifique par Tenant (`@TenantConfigurationProperties`)
Pour gérer les configurations qui varient d'un tenant à l'autre (ex: des paramètres fonctionnels), la bibliothèque fournit l'annotation `@TenantConfigurationProperties`.

Elle remplace `@ConfigurationProperties` pour ces cas d'usage. Ces beans de configuration ne peuvent pas être injectés directement. Il faut passer par le `TenantConfigurationRegistry` pour récupérer l'instance correspondant au tenant courant.

### 3. Configuration des Fonctionnalités Spécifiques

#### Verrouillage Distribué (ShedLock)
Pour activer ShedLock dans un contexte multi-tenant :

```properties
jpa.shedlock.enabled=true
```

La configuration active automatiquement :
- `@EnableScheduling` et `@EnableSchedulerLock` avec un timeout par défaut de 2 minutes
- Un `LockProvider` qui utilise la base de données avec sélection de schéma automatique
- L'isolation des verrous par tenant (chaque tenant a ses verrous dans son propre schéma)

#### Exemple d'utilisation des tâches planifiées
```java
@Component
public class MyScheduledTasks {
    
    @TenantScheduled(scheduled = @Scheduled(fixedRate = 60000))
    @SchedulerLock(name = "processData", lockAtMostFor = "50s", lockAtLeastFor = "10s")
    public void processDataForAllTenants() {
        // Cette méthode s'exécutera pour chaque tenant
        // Le contexte tenant sera automatiquement défini
        // ShedLock garantit qu'une seule instance s'exécute par tenant
    }
}
```

## Fonctionnalités Détaillées

### API REST & Feign
- **`TenantFilter`** : Ce filtre intercepte toutes les requêtes HTTP entrantes. Il lit l'en-tête `X-TENANT` pour initialiser le `TenantContext`. Il gère également une liste d'URL à exclure (comme les endpoints Actuator ou Swagger) configurable via les propriétés de l'application. Le nettoyage du contexte est garanti dans un bloc `finally`.
- **`TenantFeignInterceptor`** : Pour les appels sortants vers d'autres microservices, cet intercepteur ajoute automatiquement l'en-tête `X-TENANT`, assurant une propagation transparente du contexte.

### Kafka
La propagation du contexte dans un environnement asynchrone est gérée de manière robuste :

- **`MultitenantKafkaMessageConverter`** :
  - À la **production**, il enrichit chaque message Kafka avec l'en-tête `tenant` issus du `TenantContext`.
  - À la **consommation**, il lit cet en-tête pour reconstituer et initialiser le `TenantContext` avant que le message ne soit traité par le listener.
- **`TenantKafkaRecordInterceptor`** : C'est une pièce de sécurité essentielle. Cet intercepteur garantit que `tenantContext.clear()` est appelé après **chaque** traitement de message, que celui-ci ait réussi (`success`) ou échoué (`failure`). Cela prévient les fuites de contexte critiques dans le pool de threads des consommateurs Kafka.

### Base de Données (JPA/Hibernate)
La bibliothèque configure Hibernate pour utiliser la stratégie `SCHEMA`. Une fois le `TenantContext` initialisé, toutes les requêtes JPA/Hibernate sont automatiquement dirigées vers le bon schéma de base de données (ex: `etablissement_a_active`).

#### Stratégie de nommage physique (Physical Naming Strategy)

La configuration JPA (`TenantJpaConfig`) utilise `putIfAbsent` pour la propriété `hibernate.physical_naming_strategy`. Cela signifie que **l'application consommatrice peut choisir sa propre stratégie** en la déclarant dans ses propriétés :

```yaml
spring:
  jpa:
    properties:
      hibernate:
        # Conversion automatique camelCase → snake_case (recommandé si la BDD utilise snake_case)
        physical_naming_strategy: org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
```

Si aucune stratégie n'est déclarée par l'application, la valeur par défaut est `PhysicalNamingStrategyStandardImpl` (pas de conversion — les noms de champs Java sont utilisés tels quels comme noms de colonnes).

### Méthodes Asynchrones (@Async)

La bibliothèque fournit une propagation automatique du `TenantContext` vers les threads asynchrones via `TenantAsyncConfig` :

- **`TenantAsyncConfig`** : Implémente `AsyncConfigurer` pour que Spring utilise un `ThreadPoolTaskExecutor` avec un `TaskDecorator` qui capture et restaure le tenant courant.
- **Couverture** : Toutes les méthodes `@Async`, y compris Spring Modulith (`@ApplicationModuleListener`), héritent automatiquement du `TenantContext` du thread appelant.
- **Surcharge** : L'application consommatrice peut déclarer son propre bean `AsyncConfigurer` — dans ce cas `TenantAsyncConfig` est ignorée (`@ConditionalOnMissingBean`).
- **Thread prefix** : Les threads asynchrones sont nommés `tenant-async-*` pour faciliter le debugging.

### Cache (Spring Cache)
Lorsque le cache Spring est activé, la bibliothèque fournit un `multitenantKeyGenerator`. En l'utilisant dans vos annotations `@Cacheable`, les clés de cache sont automatiquement préfixées avec l'identifiant du tenant, garantissant une isolation parfaite des données en cache.

### Tâches Planifiées (Scheduled Tasks)
La bibliothèque étend le système de planification Spring pour supporter la multi-tenancy :

- **`@TenantScheduled`** : Cette annotation remplace `@Scheduled` pour les tâches qui doivent s'exécuter dans un contexte multi-tenant. Elle encapsule l'annotation `@Scheduled` standard.
- **`TenantScheduledAnnotationBeanPostProcessor`** : Ce processeur détecte les méthodes annotées avec `@TenantScheduled` et crée automatiquement une instance de tâche par tenant si le mode multi-schéma est actif.
- **`TenantScheduledMethodRunnable`** : Runnable spécialisé qui capture le contexte du tenant au moment de la création de la tâche et le restaure lors de l'exécution, garantissant que chaque exécution se fait dans le bon contexte.
- **Exclusion du schéma par défaut** : En définissant `schema-configuration.include-default-schema-in-scheduling=false`, les tâches planifiées ne s'exécuteront que sur les schémas tenant réels (organisations), excluant le schéma par défaut (ex: `public`).

```yaml
# Pour exclure le schéma par défaut des tâches planifiées :
schema-configuration:
  include-default-schema-in-scheduling: false
```

### Messagerie JMS
Pour la messagerie JMS, la bibliothèque propose des convertisseurs qui propagent automatiquement le contexte tenant :

- **`TenantMappingJackson2MessageConverter`** : Convertisseur principal basé sur Jackson qui :
  - À l'**envoi** : Vérifie la présence d'un tenant dans le contexte (lance une exception si absent), puis enrichit le message avec la propriété `tenant`
  - À la **réception** : Lit cette propriété pour reconstituer et initialiser le `TenantContext` avant traitement
  - Configure automatiquement le type de message en TEXT et utilise `_type` comme propriété d'identification

- **`MultitenantSimpleMessageConverter`** : Convertisseur simple pour les cas d'usage basiques, qui gère uniquement la réception en initialisant le contexte tenant à partir des propriétés du message

- **`TenantJmsConfig`** : Configuration automatique qui active les fonctionnalités JMS multi-tenant :
  - `queueJmsTemplate` : Template configuré pour les queues avec QoS explicite (priorité 4, TTL 30s)
  - `topicJmsTemplate` : Template configuré pour les topics (pub/sub)
  - `jmsListenerContainerFactory` : Factory pour les listeners avec conversion automatique du contexte

- **Activation** : Disponible uniquement avec le profil `multiSchema` et si JMS est présent dans le classpath

### Verrouillage Distribué (ShedLock)
L'intégration avec ShedLock permet de gérer les verrous distribués dans un environnement multi-tenant :

- **`SchemaSelectedMultitenantDataSource`** : DataSource personnalisé qui sélectionne automatiquement le bon schéma en fonction du contexte tenant courant. Cette classe étend `DelegatingDataSource` et intercepte les connexions pour appliquer la sélection de schéma via `SchemaSelector`.
- **`ShedlockJpaConfig`** : Configuration automatique qui active ShedLock avec le support multi-tenant. Elle crée un `LockProvider` basé sur JDBC qui utilise le schéma approprié selon le contexte tenant.
- **Configuration conditionnelle** : Activé uniquement si `jpa.shedlock.enabled=true` est défini dans les propriétés de l'application.
- **Isolation des verrous** : Chaque tenant dispose de ses propres verrous stockés dans son schéma spécifique, évitant complètement les conflits entre tenants.
- **Annotations supportées** : Fonctionne avec `@SchedulerLock` sur les méthodes `@TenantScheduled` pour garantir qu'une seule instance d'une tâche planifiée s'exécute à la fois par tenant.

### Événements d'Application
La gestion des événements Spring est adaptée pour le contexte multi-tenant avec un système sophistiqué de wrapping et de dispatch :

- **`MultiTenantEventPublisherProxy`** : Proxy dynamique qui intercepte tous les appels à `publishEvent()`. Il détecte si un tenant est présent dans le contexte et wrappe automatiquement les `ApplicationEvent` dans un `MultitenantEventWrapper` pour préserver le contexte tenant.

- **`MultitenantEventWrapper<T>`** : Conteneur qui encapsule l'événement original avec le tenant courant et un timestamp. Cette classe permet de transporter le contexte tenant avec l'événement à travers le système d'événements.

- **`MultitenantEventDispatcher`** : Composant central qui gère le dispatch des événements wrappés. Il :
  - Utilise un `ThreadLocal<Set<Object>>` pour éviter les boucles infinies lors du traitement d'événements
  - Publie d'abord l'événement wrappé, puis l'événement original avec le contexte tenant défini
  - Nettoie automatiquement le contexte dans un bloc `finally`

- **`@MultitenantEventListener`** : Annotation qui étend `@EventListener` avec support multi-tenant. Elle offre un attribut `propagateTenant` (true par défaut) pour contrôler la propagation du contexte.

- **`MultitenantListenerAspect`** : Aspect AOP qui intercepte les méthodes annotées avec `@MultitenantEventListener`. Il déwrappe automatiquement les `MultitenantEventWrapper` et défini le contexte tenant avant d'exécuter le listener.

- **Configuration automatique** : Le proxy remplace l'`ApplicationEventPublisher` par défaut via `@Primary` uniquement avec le profil `multiSchema`.

### Provisioning de Tenant à Chaud (Runtime)

La bibliothèque fournit un service de provisioning permettant de créer un nouveau tenant **pendant que l'application tourne** (et non uniquement au démarrage). C'est typiquement utilisé lors de la création d'une nouvelle organisation.

- **`TenantProvisioningService`** : Interface exposant `provision(tenantId, tenantName)` et `clearContext()`.
- **`DefaultTenantProvisioningService`** : Implémentation qui exécute séquentiellement :
  1. Validation du nom de schéma (alphanumeric + underscore, lowercase)
  2. `CREATE SCHEMA IF NOT EXISTS` (hors transaction pour visibilité immédiate)
  3. Exécution des migrations Liquibase sur le nouveau schéma
  4. Enregistrement du tenant dans le `TenantConfigurationRegistry`
  5. Positionnement du `TenantContext` sur le nouveau schéma

#### Utilisation

```java
@RequiredArgsConstructor
public class OrganisationService {
    private final TenantProvisioningService tenantProvisioningService;

    public void createOrganisation(String tenantId, String tenantName) {
        try {
            // Crée le schéma + Liquibase + positionne le TenantContext
            Tenant tenant = tenantProvisioningService.provision(tenantId, tenantName);

            // Toutes les opérations JPA suivantes utilisent le nouveau schéma
            userRepository.save(new User("admin@org.com"));
        } finally {
            tenantProvisioningService.clearContext();
        }
    }
}
```

**Points importants :**
- Après `provision()`, le `TenantContext` reste positionné sur le nouveau tenant. Cela permet d'exécuter des opérations dans le nouveau schéma (création de données initiales, utilisateur admin, etc.).
- `clearContext()` doit être appelé dans un bloc `finally` une fois les opérations terminées.
- La méthode est annotée `@Transactional(propagation = NOT_SUPPORTED)` car le DDL (`CREATE SCHEMA`) doit être commité immédiatement pour être visible par Liquibase.
- Activé automatiquement si Liquibase est dans le classpath et le profil `multiSchema` ou `multitenant` est actif.

#### Configuration du Config Manager

```yaml
multitenant:
  config-manager:
    base-url: http://localhost:8090
    registry-path: /api/config/v1/registry/{applicationName}
    application-path: /api/config/v1/applications
    auto-create-application: true
    fail-fast: false
```

## Support Technique

Pour toute question ou problème d'intégration, consultez la documentation technique des composants ou les exemples d'utilisation fournis dans le dossier `/examples`.