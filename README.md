# Pressure BLE - Native Android (Kotlin)

App Android nativa per ricevere dati di pressione via BLE dall'ESP32.

## 🚀 Setup

### Build locale
```bash
cd pressure_ble_kotlin
./gradlew assembleRelease
```

L'APK sarà in: `app/build/outputs/apk/release/`

### GitHub Actions
1. Carica su GitHub
2. Il workflow compila automaticamente
3. Scarica l'APK da Actions → Artifacts

## 📱 Funzionalità

- Scan BLE per `ESP32_Pressure`
- Connessione automatica
- Ricezione dati via notifiche BLE
- Lista storico con timestamp
- Colori indicatori per intensità

## 📡 UUIDs

- **Service:** `0000181A-0000-1000-8000-00805F9B34FB`
- **Characteristic:** `12345678-1234-5678-1234-56789ABCDEF0`

## 📋 Comandi Git

```powershell
cd "C:\Users\Mazza Marco\.gemini\antigravity\scratch\pressure_ble_kotlin"
git init
git add .
git commit -m "Initial commit: Native Kotlin BLE app"
git branch -M main
git remote add origin https://github.com/mazinganet/Pression_Sensor.git
git push -u origin main --force
```
