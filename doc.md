## Fonctionnalités de l’application mobile

### Rôle et périmètre de l’application

Cette application mobile développée dans le cadre de ce POC a pour objectif principal de **valider la faisabilité technique du suivi des trajets domicile–travail**, ainsi que l’intégration complète avec le backend de la plateforme, ce n'est pas l'application mobile qui sera présentée lors de la soutenance.

---

### Fonctionnalités principales

L’application permet actuellement de :

- Authentifier un utilisateur via JWT
- Lancer un service de détection de trajets en arrière-plan
- Détecter ou simuler des trajets de mobilité
- Afficher les trajets détectés localement et en attente de validation
- Permettre à l’utilisateur de :
  - valider un trajet
  - modifier certaines informations si nécessaire
  - rejeter un trajet
- Envoyer les trajets validés vers le backend
- Recevoir et afficher les points associés
- Consulter des statistiques simplifiées (distance totale, score)

Ce périmètre couvre **l’intégralité du cycle de vie d’un trajet** du point de vue utilisateur.

### Séparation React Native / Kotlin : choix architectural

L’application est développée majoritairement en **React Native** pour l’interface utilisateur, mais la **couche de détection des trajets** est implémentée en **Kotlin natif**.

Ce choix est **volontaire et justifié techniquement**.

#### Pourquoi ne pas avoir implémenté la détection en React Native ?

La détection de trajets repose sur :
- l’Activity Recognition API
- des services Android en arrière-plan
- un Foreground Service avec notification persistante
- des contraintes fortes liées au cycle de vie Android (battery, background, permissions)

Ces éléments :
- ne sont pas exposés par React Native
- nécessitent un contrôle fin du cycle de vie natif
- sont plus fiables et maintenables en code Android natif

Implémenter cette logique en React Native aurait :
- ajouté une couche d’abstraction inutile
- augmenté les risques de bugs liés au background
- complexifié le débogage et la maintenance

---

### Rôle de React Native dans l’architecture

React Native est utilisé pour :

- la gestion des écrans
- l’état global de l’application
- l’affichage des trajets
- les interactions utilisateur
- la communication avec le backend

La couche Kotlin expose uniquement :
- des événements (trajet détecté)
- des méthodes ciblées (activation détection, simulation)

Cette séparation permet :
- une architecture claire et modulaire
- une meilleure robustesse côté capteurs
- une portabilité maximale de l’UI

# Mode Debug & Simulation

## 📌 Contexte

Dans le cadre du POC *Green Mobility Pass* (PFE Michelin & SNCF – Movin’On), l’application mobile intègre une détection automatique des trajets basée sur l’**Activity Recognition API** d’Android.

Cette API repose sur des heuristiques système et des signaux capteurs difficiles à tester de manière déterministe, en particulier :
- en environnement **indoor**
- avec des **micro-déplacements**
- dans un contexte de **build release** sur téléphone réel

Afin de garantir la **testabilité**, la **démonstrabilité** et la **validation de la chaîne métier**, deux mécanismes complémentaires ont été implémentés :
- un **mode Debug**
- un **mode Simulation**

---

## 🎯 Objectifs de ces modes

Les modes Debug et Simulation ont pour objectifs de :

- Tester l’application **sans dépendre du contexte physique réel**
- Valider **toute la chaîne fonctionnelle** (mobile → backend)
- Accélérer le développement et le débogage
- Garantir une **démo fiable** lors de la soutenance
- Isoler les responsabilités entre :
  - acquisition capteurs
  - logique métier
  - synchronisation backend

Ces modes sont **volontaires, assumés et désactivables**.

---

## 🛠️ Mode Debug

### Description

Le **mode Debug** est un mode interne permettant d’**assouplir la détection automatique des trajets** afin de la rendre exploitable dans des conditions contraignantes (appartement, déplacements courts, escaliers, etc.).

Il reste basé sur l’Activity Recognition API, mais avec des règles simplifiées.

### Fonctionnement

Lorsque le mode Debug est activé :

- Les seuils de détection sont abaissés :
  - confiance minimale réduite
  - nombre d’événements requis réduit
- Un trajet peut être déclenché :
  - après quelques événements `ON_FOOT`
  - même pour des déplacements courts
- La **state machine** reste active et utilisée

👉 Le pipeline réel est conservé, mais **calibré pour le debug**.

### Ce que le mode Debug permet de valider

- Le bon fonctionnement du **Foreground Service**
- La réception d’événements d’activité
- La logique de la **TripStateMachine**
- La génération d’un trajet détecté (`DetectedTrip`)
- L’insertion en base locale (Room)
- La remontée des événements vers React Native

### Ce que le mode Debug ne garantit pas

- Une précision réelle de la détection en conditions de production
- Une validation des heuristiques Google en environnement outdoor

---

## 🧪 Mode Simulation

### Description

Le **mode Simulation** permet de **simuler explicitement un trajet**, sans utiliser :
- les capteurs
- les permissions Android
- l’Activity Recognition API

Il injecte directement un trajet factice dans la chaîne applicative.

Ce mode est activé manuellement depuis l’interface utilisateur.

---

### Fonctionnement

Lors de l’appel à `simulateTrip()` :

1. Un trajet local factice est créé (exemple) :
   - durée : ~10 minutes
   - distance : ~0.8 km
   - mode de transport : marche
   - confidence élevée
2. Le trajet est :
   - inséré en base locale (Room)
   - émis vers React Native comme un trajet détecté
3. Le reste du workflow est **strictement identique** à un trajet réel

---

### Ce que le mode Simulation permet de valider

Le mode Simulation valide **100 % de la chaîne métier**, à savoir :

#### Côté mobile
- Bridge React Native ↔ Kotlin
- Modèle de trajet local
- Stockage Room
- UI “trajets en attente”
- Workflow de validation utilisateur

#### Côté backend
- Authentification JWT
- Endpoint `POST /journey`
- Mapping des données (`JourneyCreate`)
- Calcul du score
- Mise à jour des statistiques utilisateur

👉 Si la simulation fonctionne de bout en bout, **la chaîne métier est validée**.

---

### Ce que le mode Simulation ne teste pas

- Les permissions Android
- L’Activity Recognition API
- Les politiques batterie / background
- Les capteurs physiques

Cela est **volontaire** et **assumé**.

---

## 🧠 Justification technique et académique

L’Activity Recognition API :
- est non déterministe
- dépend du hardware, de l’OS et du contexte
- est peu exploitable en environnement indoor

Pour un **POC académique de 1 mois**, il est pertinent de :

- isoler la logique métier
- garantir la testabilité
- démontrer la valeur fonctionnelle

Le mode Simulation agit comme un **banc d’essai**, tandis que la détection réelle constitue un axe d’amélioration futur.

---

## 🎓 Positionnement pour la soutenance

Formulation recommandée :

> *« La détection automatique repose sur l’Activity Recognition API, difficile à tester de manière déterministe en intérieur. Afin de garantir la testabilité et la démonstration du prototype, nous avons intégré un mode debug et un mode simulation. La logique métier complète est validée et la détection réelle reste branchée sur cette chaîne. »*

---

## 🔮 Perspectives d’évolution

Ces modes sont destinés à être :
- désactivés en production
- remplacés ou complétés par :
  - un calibrage terrain
  - des données réelles
  - une segmentation multi-modale
  - une validation serveur

Ils constituent une **base saine** pour des versions ultérieures.

---

## ✅ Conclusion

- Le **mode Debug** facilite le développement et le test capteur
- Le **mode Simulation** garantit la validation fonctionnelle complète
- Ensemble, ils assurent un **POC robuste, démontrable et défendable**

Ces choix sont alignés avec les contraintes du projet, les bonnes pratiques d’ingénierie logicielle et les attentes académiques.
