# iBeacon Detector Test BLE Protocol

Questo documento descrive il protocollo BLE offline usato da `ibeacon-detector-test.yaml`.

## Obiettivo

- mantenere l'apriporta Home Assistant invariato
- aggiungere un canale BLE locale di emergenza
- permettere test manuali con `nRF Connect` e Termux

## Servizio BLE

- Service UUID: `19b10000-e8f2-537e-4f6c-d104768a1214`

## Characteristics

- Challenge UUID: `19b10001-e8f2-537e-4f6c-d104768a1214`
- Response UUID: `19b10002-e8f2-537e-4f6c-d104768a1214`
- Status UUID: `19b10003-e8f2-537e-4f6c-d104768a1214`
- Debug UUID: `19b10004-e8f2-537e-4f6c-d104768a1214`

## Flusso

1. L'app Android si connette al device BLE.
2. L'ESP32 genera una challenge casuale di 8 caratteri hex uppercase.
3. L'app legge la challenge.
4. L'app calcola la response come FNV-1a 32 bit uppercase hex della stringa:

```text
<CHALLENGE>:<SECRET>
```

5. L'app scrive la response sulla characteristic `Response`.
6. Se valida, il relay si attiva e viene generata una nuova challenge.

## Secret di test

```text
door-test-secret-2026
```

## Status values

- `CHALLENGE_READY`
- `OK`
- `ERR`
- `TIMEOUT`
- `LOCKED`

## Debug values

Esempi:

- `attempts=0 challenge_age_s=4`
- `attempts=1 auth=err`
- `attempts=3 lockout_s=27`
- `attempts=0 auth=ok`

## Regole lato client Android

1. Connettersi al device `ibeacon-detector-test`.
2. Leggere `Challenge`.
3. Calcolare `Response`.
4. Scrivere `Response` come stringa ASCII uppercase di 8 caratteri.
5. Leggere `Status`.
6. Se `OK`, considerare la porta aperta.
7. Se `ERR`, `TIMEOUT` o `LOCKED`, leggere di nuovo `Challenge` e `Debug`.

## Timeout e lockout

- La challenge scade dopo 30 secondi.
- Dopo 3 errori consecutivi si attiva un lockout di 30 secondi.

## Test rapido con Termux

Comando:

```bash
python ble_fnv1a.py A1B2C3D4
```

Output atteso:

```text
challenge: A1B2C3D4
payload:   A1B2C3D4:door-test-secret-2026
response:  XXXXXXXX
```

La stringa `response` va scritta sulla characteristic `Response`.
