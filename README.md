# Selene Data App

Android-App (Honor Magic V5) für die Bordnetz-Daten der Segelyacht **Selene** (Grand Soleil 50).
Datenquelle ist der Signal-K-Server auf dem Victron Cerbo GX.

## Installieren
1. Auf dem Handy die Seite **Releases → „Neueste Version“** dieses Repos öffnen.
2. `Selene-Data-App.apk` herunterladen und öffnen. Beim ersten Mal die Installation aus dieser Quelle erlauben.
3. Neue Versionen lassen sich einfach drüberinstallieren.

Jeder Push auf `main` baut die APK automatisch neu (GitHub Actions).

## Seiten
- **Segeln**: Windinstrument (AWA/AWS, TWA), Fahrt, Tiefe, Kurse, Autopilot-Status
- **Lage**: Krängung und Trimm (vom Airmar DST820, PGN 127257), Maximalwerte
- **Energie**: Batterien (Smart Shunts), Solar, weitere Victron-Werte
- **Karte**: OpenStreetMap + OpenSeaMap, eigenes Boot, Track, AIS mit CPA/TCPA
- **Anker**: Ankerwache mit Alarmradius, Ton und Vibration
- **Daten**: alle empfangenen Signal-K-Werte mit Filter (zum Prüfen, was ankommt)

Aufgeklappt (Foldable) zeigen die Seiten mehr Inhalte nebeneinander.

## Datenquellen (Einstellungen ⚙)
- **Simulator**: erzeugt Testdaten, funktioniert ohne Netz
- **Signal-K-Demoserver**: öffentliche Demo-Daten (demo.signalk.org)
- **Selene (Bordnetz)**: Signal K auf dem Cerbo, z. B. `einstein:3000` oder `IP:3000`

Batterienamen: In den Einstellungen `Kennung=Name` je Zeile eintragen (Kennung steht auf der Seite „Daten“ unter `electrical.batteries.<Kennung>`).

## Technik
Kotlin, Jetpack Compose, OkHttp (WebSocket), osmdroid. Signal-K-Stream `/signalk/v1/stream?subscribe=all`.
