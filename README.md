# Green Mobility Pass

Application mobile Android pour le suivi des trajets de mobilite verte avec detection automatique des activites.

## Description

Green Mobility Pass est un POC (Proof of Concept) d'application mobile qui :
- Detecte automatiquement les trajets en arriere-plan via l'Activity Recognition API de Google
- Stocke les trajets detectes localement dans une base Room
- Permet a l'utilisateur de valider et corriger les trajets avant envoi
- Envoie les trajets valides au backend FastAPI
- Affiche les points (score) gagnes pour chaque trajet

## Architecture

```
+----------------------------------------------------------+
|                    React Native (TypeScript)              |
|  +--------------+ +--------------+ +--------------------+ |
|  | LoginScreen  | | HomeScreen   | | JourneysScreens    | |
|  +--------------+ +--------------+ +--------------------+ |
|                          |                                |
|                   +------+------+                         |
|                   | API Client  |                         |
|                   +------+------+                         |
+--------------------------|--------------------------------+
|               Native Module Bridge                        |
+--------------------------|--------------------------------+
|                    Kotlin Layer                           |
|  +----------------------+ +-----------------------------+ |
|  | TripDetectionService | | Room Database               | |
|  | (Foreground Service) | | +-------------------------+ | |
|  +----------+-----------+ | | LocalJourney Entity     | | |
|             |             | +-------------------------+ | |
|  +----------+-----------+ +-----------------------------+ |
|  | Activity Recognition |                                 |
|  | + State Machine      |                                 |
|  +----------------------+                                 |
+-----------------------------------------------------------+
```

## Prerequis

### Environnement de developpement

| Outil | Version requise |
|-------|-----------------|
| Node.js | 18.x LTS |
| JDK | 17 |
| Android Studio | Latest stable |
| Android SDK | 34 |

### Versions du projet (figees)

| Composant | Version |
|-----------|---------|
| React Native | 0.73.6 |
| Kotlin | 1.9.10 |
| Android Gradle Plugin | 8.1.1 |
| Gradle | 8.1 |
| compileSdkVersion | 34 |
| targetSdkVersion | 34 |
| minSdkVersion | 26 |

## Installation

### 1. Cloner le projet

```bash
git clone <repository-url>
cd GreenMobilityPass
```

### 2. Installer les dependances Node

```bash
npm install
```

### 3. Verifier l'environnement

```bash
# Verifier la version de Node
node --version  # Doit etre 18.x

# Verifier la version de Java
java --version  # Doit etre 17.x

# Verifier Gradle
cd android && ./gradlew -v && cd ..

# Diagnostic React Native
npx react-native doctor
```

### 4. Configuration du backend

Modifier l'URL du backend dans `src/api/client.ts` :

```typescript
// Pour un emulateur Android
const API_BASE_URL = 'http://10.0.2.2:8000';

// Pour un appareil physique (remplacer par l'IP de votre serveur)
const API_BASE_URL = 'http://192.168.x.x:8000';
```

## Lancement

### Demarrer Metro Bundler

```bash
npx react-native start
```

### Lancer sur Android

Dans un nouveau terminal :

```bash
npx react-native run-android
```

Ou via Android Studio :
1. Ouvrir le dossier `android/` dans Android Studio
2. Attendre la synchronisation Gradle
3. Lancer sur un emulateur ou appareil connecte

## Structure du projet

```
GreenMobilityPass/
|-- android/
|   +-- app/src/main/java/com/greenmobilitypass/
|       |-- bridge/
|       |   |-- TripDetectionModule.kt    # Native Module
|       |   +-- TripDetectionPackage.kt
|       |-- database/
|       |   |-- AppDatabase.kt            # Room Database
|       |   |-- LocalJourney.kt           # Entity
|       |   +-- LocalJourneyDao.kt        # DAO
|       |-- detection/
|       |   |-- ActivityRecognitionReceiver.kt
|       |   |-- BootReceiver.kt
|       |   |-- TripDetectionService.kt   # Foreground Service
|       |   |-- TripState.kt
|       |   +-- TripStateMachine.kt       # State Machine
|       |-- MainActivity.kt
|       +-- MainApplication.kt
|-- src/
|   |-- api/
|   |   |-- client.ts                     # API Client
|   |   +-- types.ts                      # TypeScript types
|   |-- context/
|   |   +-- AuthContext.tsx               # Auth state
|   |-- native/
|   |   +-- TripDetection.ts              # Native module wrapper
|   |-- navigation/
|   |   +-- AppNavigator.tsx              # Navigation setup
|   +-- screens/
|       |-- HomeScreen.tsx
|       |-- LoginScreen.tsx
|       |-- PendingJourneyDetailScreen.tsx
|       |-- PendingJourneysScreen.tsx
|       +-- ValidatedJourneysScreen.tsx
|-- App.tsx
+-- package.json
```

## Fonctionnalites

### Detection automatique

La machine a etats de detection fonctionne ainsi :

1. **Etat IDLE** : En attente d'activite
   - Passe a IN_TRIP si activite != STILL avec confidence >= 60% pendant >= 2 min

2. **Etat IN_TRIP** : Trajet en cours
   - Enregistre les activites detectees
   - Passe a ENDED si STILL avec confidence >= 60% pendant >= 3 min

3. **Etat ENDED** : Trajet termine
   - Calcule le mode dominant, la duree, la distance estimee
   - Sauvegarde dans Room
   - Retourne a IDLE

### Estimation de distance

| Mode | Vitesse estimee |
|------|-----------------|
| A pied | 5 km/h |
| Course | 10 km/h |
| Velo | 15 km/h |
| Vehicule | 40 km/h |

`distance = duree_heures x vitesse`

### Permissions requises

- `ACTIVITY_RECOGNITION` : Detection des activites
- `FOREGROUND_SERVICE` : Service en arriere-plan
- `POST_NOTIFICATIONS` : Notification du service (Android 13+)

## API Backend

L'application consomme les endpoints suivants :

| Endpoint | Methode | Description |
|----------|---------|-------------|
| `/token` | POST | Authentification (OAuth2) |
| `/token/refresh` | POST | Rafraichissement du token |
| `/me` | GET | Informations utilisateur |
| `/journey/` | POST | Creer un trajet |
| `/journey/validated` | GET | Trajets valides |
| `/journey/statistics/me` | GET | Statistiques utilisateur |

### Format d'un trajet (JourneyCreate)

```json
{
  "place_departure": "string",
  "place_arrival": "string",
  "time_departure": "2024-01-15T08:30:00Z",
  "time_arrival": "2024-01-15T08:45:00Z",
  "distance_km": 2.5,
  "transport_type": "marche",
  "detection_source": "auto"
}
```

Types de transport : `marche`, `velo`, `transport_commun`, `voiture`

## Checklist de validation

- [ ] Le projet s'initialise sans erreur (`npm install`)
- [ ] L'app build avec Java 17 / Gradle 8.1 / AGP 8.1.1
- [ ] La detection background fonctionne (notification visible)
- [ ] Un trajet local est cree automatiquement apres detection
- [ ] L'UI affiche les trajets en attente
- [ ] La validation envoie POST /journey/
- [ ] Le score retourne est affiche dans la popup
- [ ] Les stats backend sont visibles sur l'ecran d'accueil

## Depannage

### Erreur de build Gradle

```bash
cd android
./gradlew clean
./gradlew assembleDebug
```

### Permissions non accordees

Sur Android 10+, les permissions d'activite doivent etre accordees manuellement :
1. Aller dans Parametres > Applications > Green Mobility Pass
2. Permissions > Activite physique > Autoriser

### Metro bundler bloque

```bash
npx react-native start --reset-cache
```

### Probleme de connexion API

- Verifier que le backend est lance
- Verifier l'URL dans `src/api/client.ts`
- Pour emulateur Android : utiliser `10.0.2.2` au lieu de `localhost`

## Technologies utilisees

- **React Native** 0.73.6 - Framework mobile cross-platform
- **TypeScript** - Typage statique
- **Kotlin** - Couche native Android
- **Room** - Base de donnees locale SQLite
- **Activity Recognition API** - Detection d'activites Google
- **React Navigation** - Navigation entre ecrans
- **AsyncStorage** - Stockage des tokens

## Licence

Ce projet est un POC a des fins de demonstration.
