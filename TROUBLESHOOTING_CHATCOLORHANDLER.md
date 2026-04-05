# Résolution de l'erreur ChatColorHandler

## Symptôme

```
[ERROR] Could not resolve dependencies for project fr.wpets:Wpets:jar:1.0.0
[ERROR] dependency: me.dave:ChatColorHandler:jar:v2.5.3 (provided)
[ERROR] me.dave:ChatColorHandler:jar:v2.5.3 was not found in https://repo.papermc.io/repository/maven-public/
```

## Cause

Cette erreur est due à un **cache Maven obsolète** qui tente de résoudre une dépendance qui n'existe pas et n'a jamais existé dans ce projet. Le projet Wpets n'utilise pas et n'a jamais utilisé ChatColorHandler.

## Solution

### Option 1 : Supprimer le cache spécifique (Recommandé)

```bash
# Supprimer le cache de ChatColorHandler uniquement
rm -rf ~/.m2/repository/me/dave/ChatColorHandler

# Nettoyer et reconstruire le projet
mvn clean install
```

### Option 2 : Purger toutes les dépendances en échec

```bash
# Forcer la mise à jour de toutes les dépendances
mvn dependency:purge-local-repository

# Reconstruire
mvn clean install
```

### Option 3 : Nettoyage complet du cache Maven

```bash
# Supprimer tout le cache local Maven (attention : téléchargera à nouveau toutes les dépendances)
rm -rf ~/.m2/repository

# Reconstruire
mvn clean install
```

### Option 4 : Forcer la mise à jour depuis les dépôts distants

```bash
# Forcer Maven à vérifier les mises à jour
mvn clean install -U

# L'option -U force la mise à jour des snapshots et releases
```

## Vérification

Après avoir appliqué l'une des solutions ci-dessus, vérifiez que le build fonctionne :

```bash
mvn clean package
```

Le build devrait se terminer avec `BUILD SUCCESS`.

## Dépendances réelles du projet

Pour référence, voici les vraies dépendances du projet Wpets :

- **Paper API** 1.21.4 (fournie par le serveur)
- **MythicMobs** 5.6.1 (fournie par le serveur)
- **ModelEngine** R4.0.9 (fournie par le serveur)
- **FancyHolograms** 2.3.0 (fournie par le serveur)
- **SQLite JDBC** 3.45.1.0 (intégrée dans le JAR)
- **MySQL Connector** 8.3.0 (intégrée dans le JAR)

**ChatColorHandler n'est PAS une dépendance de ce projet.**

## Note pour les développeurs

Si vous rencontrez toujours cette erreur après avoir appliqué ces solutions, il se peut que :

1. Un autre projet sur votre machine utilise ChatColorHandler et a pollué votre cache Maven
2. Un script de build personnalisé tente d'ajouter cette dépendance
3. Votre IDE (IntelliJ IDEA, Eclipse, VSCode) a mis en cache une configuration erronée

Dans ce cas, essayez également :

```bash
# Pour IntelliJ IDEA
rm -rf .idea/
mvn idea:clean idea:idea

# Pour Eclipse
mvn eclipse:clean eclipse:eclipse

# Puis réimporter le projet dans votre IDE
```
