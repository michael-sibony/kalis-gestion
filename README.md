# Kalis Gestion

Progiciel de gestion commerciale multi-clients sur **Jakarta EE 10**, servi par **WildFly 41** sous **Java 21**.

Un même cœur standard sert plusieurs clients. Ce qui est propre à un client (remise par paliers, indexation matière, exonération intracommunautaire...) vit dans un **spécifique** isolé, découvert par CDI, sans une ligne modifiée dans le cœur.

## Ce que fait l'application

- **Chiffrage** : un panier passe par les règles du cœur puis par celles du client. La pièce affiche l'écart dû aux spécifiques et la trace de chaque règle exécutée (ordre, classe, effet sur le HT).
- **Paramétrage** : chaque règle déclare ses paramètres avec bornes et pas. L'intégrateur les règle par client ; chaque changement est validé, enregistré et journalisé.
- **Reprise de données** : import des articles exportés de l'ancien logiciel du client. Contrôle ligne par ligne (colonnes, code, prix, taux de TVA français, doublons), puis intégration des seules lignes valides : création ou mise à jour.
- **Migration Java EE 8 vers Jakarta EE 10** : conversion des sources, descripteurs (`persistence.xml`, `beans.xml`, `web.xml`) et `pom.xml`. Les paquets du JDK (`javax.sql`, `javax.naming`, `javax.transaction.xa`, `javax.annotation.processing`...) restent en `javax`. Les points qui ne se corrigent pas mécaniquement sont signalés : beans gérés JSF, JAXB hors serveur, API Criteria d'Hibernate 5, découverte CDI 4.0.
- **Plateforme** : serveur, JVM, base, santé et règles chargées, lus en direct.

## Ajouter un spécifique client

```java
@ApplicationScoped
@Specifique("NEGOCE")
public class FrancoDePort implements RegleTarifaire {
    public int ordre() { return 500; }
    public String libelle() { return "Franco de port"; }
    public List<DefinitionParametre> parametres() { ... }
    public void appliquer(Chiffrage c) { ... }
}
```

La classe est découverte par CDI (`@Any Instance<RegleTarifaire>`), ses paramètres apparaissent dans l'écran de paramétrage, et elle ne s'exécute que pour l'instance `NEGOCE`. Le moteur (`MoteurTarification`) ne dépend pas du conteneur : il se teste en JUnit pur.

## Pile

| Couche | Choix |
|---|---|
| API REST | Jakarta RESTful Web Services, JSON-B |
| Injection | CDI 4.0, EJB `@Singleton @Startup` pour l'initialisation |
| Persistance | Jakarta Persistence 3, JTA, `java:comp/DefaultDataSource` |
| Validation | Jakarta Bean Validation |
| Serveur | WildFly 41.0.1.Final, Java 21 |
| Tests | JUnit 5, 43 tests |

## Lancer

```bash
docker build -t kalis-gestion .
docker run -p 8080:8080 kalis-gestion
```

L'image ne se construit pas si un test échoue (`mvn verify` dans l'étape de build). L'application répond sur `http://localhost:8080`, la santé sur `/api/sante`.

En recette, l'unité de persistance prend la source par défaut du serveur. Chez un client, on pointe `jta-data-source` vers sa base (PostgreSQL, Oracle, SQL Server) déclarée dans WildFly, sans recompiler.

## Arborescence

```
src/main/java/fr/kalis/gestion
  tarif/            moteur, règles du cœur, trace
  specifique/       un paquet par client : negoce, vauban, export
  reprise/          contrôle des fichiers de reprise
  migration/        conversion javax vers jakarta
  domaine/          entités JPA
  service/          services transactionnels CDI
  api/              ressources REST
src/main/webapp     interface
```
