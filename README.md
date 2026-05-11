# ESPnuki Door Controller

Questo repository si trova nella cartella base ESPHome:

`\\10.0.0.12\config\esphome`

La cartella contiene anche molte altre configurazioni ESPHome non legate a questo progetto. Il repository deve tracciare solo i file del controller porta ESPnuki e la documentazione collegata.

## File tracciati

- `ibeacon-detector.yaml`: configurazione ESPHome principale del controller porta.
- `ibeacon-detector-test.yaml`: configurazione di test.
- `ibeacon-detector-test-ble.md`: note/test BLE.
- `README.md`: note operative del progetto.
- `.gitignore`: regole per evitare di includere altre configurazioni o segreti.

## App Android

L'app Android collegata a questo progetto ESPHome si trova fuori da questa cartella:

`C:\Users\webma\AndroidProjects\door-app`

L'app usa Jetpack Compose e comunica via BLE con il controller ESPnuki.

La localizzazione dell'app Android e' gestita con le risorse Android standard:

- `app/src/main/res/values/strings.xml`: inglese, usato come fallback/default per lingue non italiane.
- `app/src/main/res/values-it/strings.xml`: italiano, usato quando la lingua di sistema e' italiana.

Android sceglie automaticamente la lingua in base alla lingua di sistema del dispositivo. Al momento non e' presente un selettore nelle impostazioni dell'app per forzare manualmente la lingua.

Per verificare la build dell'app Android:

```powershell
cd C:\Users\webma\AndroidProjects\door-app
.\gradlew.bat assembleDebug
```

## Gitignore

Questa directory e' la base ESPHome e include file sensibili come `secrets.yaml` e configurazioni non correlate al controller porta. Per questo motivo `.gitignore` ignora tutto per default e ri-include solo i file necessari al progetto ESPnuki.

Regole importanti:

- Non tracciare `secrets.yaml`.
- Non tracciare le altre configurazioni ESPHome presenti nella cartella base.
- Non tracciare output di build, cache o file temporanei.
- Se serve aggiungere nuovi file al progetto porta, aggiungere una regola esplicita in `.gitignore`.
