# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased
### Added
- **SPI de résolution de tenant** (`multitenant.filter.mode=resolver`) : interface `TenantResolver`
  (chaîne de stratégies ordonnées, IdP-agnostique) et `TenantAccessValidator` (politique
  d'autorisation applicative). La librairie fournit `HeaderTenantResolver` (`X-TENANT`) ;
  les applications déclarent leurs propres résolveurs (claim JWT, sous-domaine, etc.).
- `TenantResolutionFilter` : filtre pré-authentification (ordre -200 par défaut) positionnant
  le contexte tenant en best-effort avant Spring Security, pour que les accès BDD réalisés
  pendant l'authentification s'exécutent sur le bon schéma.
- `TenantEnforcementFilter` : filtre post-authentification (ordre 10 par défaut) appliquant
  la décision du `TenantAccessValidator` (PROCEED / PROCEED_WITHOUT_TENANT / REJECT).
- `MultitenantFilterProperties` (`multitenant.filter.*`) : mode, ordres, exclusions pré-auth.
- `TenantExecutor` : primitive d'exécution cross-tenant (`runAs`, `callAs`, `forEachTenant`,
  `forEachSchedulableTenant`) avec restauration garantie du contexte précédent.
- `TenantProvisioningService.deprovision(tenantName)` : suppression du schéma
  (DROP SCHEMA … CASCADE) et retrait du registry local (compensation/suppression d'organisation).
- `SchemaAdmin.dropSchemaIfExists(schemaName)`.
- `ConfigManagerClient` : cycle de vie complet des tenants côté Config Manager —
  `createTenant` (create-or-get), `listTenants`, `findTenantIdByName`, `getTenantProperties`,
  `updateTenantProperties`, `deleteTenant` (propriété `multitenant.config-manager.tenants-path`).

### Fixed
- `TenantFilter` : le nettoyage du `TenantContext` est désormais réellement garanti dans un
  bloc `finally` (une exception dans la chaîne laissait le tenant attaché au thread du pool).
- `TenantContext.clear()` ne retire plus que la clé MDC `tenant` au lieu d'effacer tout le MDC
  (correlation ids applicatifs préservés).
- `TenantContext.getCurrentSchemaName()` ne loggue plus en INFO à chaque appel (passage en TRACE).
- `SchemaBasedMultiTenantConnectionProvider` : le log `selected schema` passe de INFO à TRACE
  (émis à chaque acquisition de connexion JDBC, il noyait les logs applicatifs).

### Changed
- Le `TenantFilter` historique est conditionné à `multitenant.filter.mode=legacy`
  (valeur par défaut — aucun changement pour les applications existantes).

### Removed
- Fichiers de test résiduels `TenantFilterTest_CLEAN` / `TenantFilterTest_NEW`.

## 1.5.0 - 2025-11-07
### Added
- Switch school year history with no-multitenant ([EEN-fix-switch-school-year-history](https://msdevopsjira.fsc.atos-services.net/browse/fix-switch-school-year-history))
## 1.5.0 - 2025-10-22
### Added
- Apply no multitenant to school year [EEN-configurePipelines](https://msdevopsjira.fsc.atos-services.net/browse/EEN-configurePipelines)

## 1.4.0 - 2025-09-02
### Added
- Apply no multitenant profile ([EEN-correction-for-no-multitenant](https://msdevopsjira.fsc.atos-services.net/browse/correction-for-no-multitenant))

## 1.3.0 - 2025-08-27
### Added
- Add swagger configuration [EEN-add-swagger-configuration](https://msdevopsjira.fsc.atos-services.net/browse/EEN-add-swagger-configuration)

## 1.2.0 - 2025-08-27
### Added
- Add properties exclude urls [EEN-add-properties-exclude-urls](https://msdevopsjira.fsc.atos-services.net/browse/EEN-add-properties-exclude-urls)

## 1.1.0 - 2025-08-26
### Added
- Configure appevent [EEN-appevent](https://msdevopsjira.fsc.atos-services.net/browse/EEN-appevent)

## 1.0.0 - 2025-08-25
### Added
- Initialization project [EEN-configurePipelines](https://msdevopsjira.fsc.atos-services.net/browse/EEN-configurePipelines)
