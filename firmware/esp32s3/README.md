# ESP32-S3 Firmware

Questa cartella contiene la parte firmware del progetto `my_ESPnuki`.

File principali:

- `ibeacon-detector-test.yaml`: configurazione ESPHome del device BLE
- `secrets.example.yaml`: esempio dei segreti da creare localmente

## Nota

Il file YAML pubblicato e' sanitizzato e non contiene segreti reali.

Prima di compilare il firmware:

1. copia `secrets.example.yaml` in un tuo file locale `secrets.yaml`
2. inserisci i valori reali
3. compila il firmware con la tua installazione ESPHome
