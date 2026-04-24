# my_ESPnuki

Progetto personale composto da due parti che lavorano insieme su BLE:

1. firmware ESPHome per `ESP32-S3`
2. app Android in Kotlin/Compose

L'obiettivo e' offrire un canale secondario locale per aprire e controllare la serratura anche quando Wi-Fi o Home Assistant non sono disponibili.

## Repository

Repository pubblico:

`https://github.com/naamah75/my_ESPnuki`

## Architettura

### 1. Firmware ESP32-S3

La parte firmware espone via BLE:

- challenge di autenticazione
- characteristic di risposta
- status e debug
- snapshot diagnostico letto dall'app Android
- characteristic comandi per azioni secondarie come restart e automazione apertura

Il firmware incluso nel repository si trova in:

- `firmware/esp32s3/ibeacon-detector-test.yaml`
- `firmware/esp32s3/secrets.example.yaml`

### 2. App Android

L'app Android:

- rileva la periferica BLE
- esegue challenge/response locale
- mostra stato lock e diagnostica periferica
- consente alcune azioni locali via BLE
- puo' proteggere le azioni sensibili con impronta digitale o PIN tramite il framework Android

## Caratteristiche principali

- UI ispirata a Nuki
- unlock locale via BLE
- lettura diagnostica periferica senza dipendere da Home Assistant
- stima qualitativa della prossimita' BLE (`a portata`, `vicina`, `debole`, `fuori zona`)
- supporto biometria/PIN opzionale per le azioni sensibili

## Sicurezza e dati sensibili

Il repository pubblico non include segreti reali.

Sono stati esclusi o spostati fuori dal codice versionato:

- secret BLE condiviso
- chiave API ESPHome
- password OTA
- password fallback AP
- password Wi-Fi

### Android

Per usare davvero lo sblocco BLE devi aggiungere il secret locale in `local.properties`:

```properties
bleSharedSecret=YOUR_REAL_SECRET
```

Se il secret non e' presente, l'app continua a compilare ma lo sblocco BLE viene bloccato con un messaggio esplicito.

### ESPHome

Nel firmware i valori sensibili sono referenziati tramite `!secret`.

Parti dal file:

- `firmware/esp32s3/secrets.example.yaml`

e crea la tua versione locale non pubblica con i valori reali.

## Struttura repository

```text
app/
firmware/
  esp32s3/
    ibeacon-detector-test.yaml
    secrets.example.yaml
README.md
```

## Requisiti Android

- Android Studio recente
- JDK 17
- telefono con BLE
- permessi BLE runtime
- per alcuni dispositivi Android recenti e' utile concedere anche `ACCESS_FINE_LOCATION`

## Build Android

```bash
./gradlew :app:assembleDebug
```

Su Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

## Note operative

- gli UUID BLE restano stabili finche' non vengono cambiati nel firmware
- cambiare ESP32 non cambia automaticamente gli UUID
- la stima di distanza BLE e' qualitativa, non metrica reale

## Stato del progetto

Il progetto e' pensato come base funzionante e pubblicabile, ma resta volutamente configurabile per l'ambiente reale dell'utente.
