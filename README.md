# Door App

Progetto Android locale creato in:

`C:\Users\webma\AndroidProjects\door-app`

## Stato attuale

- struttura Android Studio pronta
- Kotlin + Compose
- UUID BLE gia' separati in `BleDoorConfig.kt`
- utility FNV-1a gia' pronta in `Fnv1a.kt`
- UI minima con pulsante `Apri porta`

## Mancano ancora

- scan BLE del device `ibeacon-detector-test`
- connessione GATT
- read challenge
- write response
- read status/debug
- gestione permessi runtime Android 12+

## File chiave

- `app/src/main/java/com/example/doorapp/BleDoorConfig.kt`
- `app/src/main/java/com/example/doorapp/Fnv1a.kt`
- `app/src/main/java/com/example/doorapp/BleDoorController.kt`
- `app/src/main/java/com/example/doorapp/MainViewModel.kt`
- `app/src/main/java/com/example/doorapp/MainActivity.kt`

## Nota

Il secret BLE e' ancora in `BleDoorConfig.kt` per velocita' di sviluppo. Prima della versione finale andra' spostato in una soluzione piu' robusta.
