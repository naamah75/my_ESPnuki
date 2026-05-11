# my_ESPnuki

Progetto personale composto da due parti che lavorano insieme su BLE:

1. firmware ESPHome per ESP32-S3
2. app Android in Kotlin/Compose

L'obiettivo e' offrire un canale secondario locale per aprire e controllare la serratura anche quando Wi-Fi o Home Assistant non sono disponibili.

## Repository

Repository pubblico:

`https://github.com/naamah75/my_ESPnuki`

Il branch `master` contiene sia l'app Android sia i file ESPHome collegati al controller porta.

## Percorsi locali

Cartella locale dell'app Android:

`C:\Users\webma\AndroidProjects\door-app`

Cartella base ESPHome locale:

`\\10.0.0.12\config\esphome`

La cartella ESPHome locale contiene anche molte altre configurazioni non legate a questo progetto. Nel repository vanno tracciati solo i file del controller porta ESPnuki e la documentazione collegata.

## Architettura

### Firmware ESPHome

La parte firmware espone via BLE:

- challenge di autenticazione
- characteristic di risposta
- status e debug
- snapshot diagnostico letto dall'app Android
- characteristic comandi per azioni secondarie come restart e automazione apertura

File ESPHome tracciati nel repository:

- `ibeacon-detector.yaml`: configurazione ESPHome principale del controller porta
- `ibeacon-detector-test.yaml`: configurazione di test
- `ibeacon-detector-test-ble.md`: note/test BLE

### App Android

L'app Android:

- rileva la periferica BLE
- esegue challenge/response locale
- mostra stato lock e diagnostica periferica
- consente alcune azioni locali via BLE
- puo' proteggere le azioni sensibili con impronta digitale o PIN tramite il framework Android

## Localizzazione Android

La localizzazione dell'app Android e' gestita con le risorse Android standard:

- `app/src/main/res/values/strings.xml`: inglese, usato come fallback/default per lingue non italiane
- `app/src/main/res/values-it/strings.xml`: italiano, usato quando la lingua di sistema e' italiana

Android sceglie automaticamente la lingua in base alla lingua di sistema del dispositivo. Al momento non e' presente un selettore nelle impostazioni dell'app per forzare manualmente la lingua.

## Caratteristiche principali

- UI ispirata a Nuki
- unlock locale via BLE
- lettura diagnostica periferica senza dipendere da Home Assistant
- stima qualitativa della prossimita' BLE (`a portata`, `vicina`, `debole`, `fuori zona`)
- supporto biometria/PIN opzionale per le azioni sensibili

## Sicurezza e dati sensibili

Il repository pubblico non include segreti reali.

Sono esclusi o da tenere fuori dal codice versionato:

- secret BLE condiviso
- chiave API ESPHome
- password OTA
- password fallback AP
- password Wi-Fi
- chiavi di firma Android
- `local.properties`
- `secrets.yaml`

### Android

Per usare davvero lo sblocco BLE l'app deve conoscere il `BLE shared secret`.

Puoi configurarlo in due modi:

1. direttamente nell'app, in `Impostazioni` -> `Accesso BLE`
2. in fase di build tramite `local.properties`:

```properties
bleSharedSecret=YOUR_REAL_SECRET
```

Se il secret non e' presente, l'app continua a compilare ma lo sblocco BLE viene bloccato con un messaggio esplicito.

Il valore da inserire nell'app deve essere identico a quello configurato nel firmware ESPHome con la chiave `ble_shared_secret`.

### ESPHome

Nel firmware i valori sensibili devono essere referenziati tramite `!secret`.

Nel file locale non pubblico `secrets.yaml`, il secret BLE ha una voce di questo tipo:

```yaml
ble_shared_secret: "YOUR_REAL_SECRET"
```

## Struttura repository

```text
app/
ibeacon-detector.yaml
ibeacon-detector-test.yaml
ibeacon-detector-test-ble.md
README.md
```

## Build Android

```powershell
cd C:\Users\webma\AndroidProjects\door-app
.\gradlew.bat :app:assembleDebug
```

## Note Gitignore

Il repository deve ignorare build Android, configurazioni locali, segreti e chiavi di firma. In particolare:

- non tracciare `secrets.yaml`
- non tracciare `local.properties`
- non tracciare keystore o certificati di firma
- non tracciare output di build, cache o file temporanei

Se serve aggiungere nuove configurazioni ESPHome al progetto porta, aggiungerle esplicitamente e verificare che non contengano segreti reali.

## Note operative

- gli UUID BLE restano stabili finche' non vengono cambiati nel firmware
- cambiare ESP32 non cambia automaticamente gli UUID
- la stima di distanza BLE e' qualitativa, non metrica reale

## Stato del progetto

Il progetto e' pensato come base funzionante e pubblicabile, ma resta volutamente configurabile per l'ambiente reale dell'utente.
