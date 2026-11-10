# TubeConvert

App Android (Kotlin + Jetpack Compose) per scaricare **audio** e **video** da YouTube: incolli il link, scegli il formato e il file viene salvato sul telefono, con avanzamento, velocità e possibilità di annullare.

---

## Funzionalità

- Analisi del link con anteprima (titolo, autore, miniatura) prima di scaricare.
- **Audio M4A (AAC)** a 128 kbps o 48 kbps.
- **Video MP4** a 1080p / 720p / 480p / 360p, con audio unito al video **senza ricodifica**.
- Coda di download multipla con stato per ogni attività (in attesa, estrazione, download, completata, fallita).
- Annullamento di un download in corso ed eliminazione del file parziale.
- Salvataggio in `Music`/`Movies` oppure in una cartella scelta dall'utente (Storage Access Framework).
- Libreria dei file scaricati con ricerca, riproduzione audio integrata e apertura del video.
- Messaggi di errore in italiano, con il dettaglio restituito da YouTube.

> L'audio viene salvato in **M4A/AAC** e non in MP3: MP3 richiederebbe un encoder sul dispositivo (ffmpeg, ~140 MB), mentre l'AAC che YouTube fornisce si riproduce su qualsiasi player Android senza conversioni.

---

## Requisiti

| Voce | Valore |
|---|---|
| Sistema | Android 7.0 o successivo (`minSdk 24`) |
| Compilazione | JDK 21, Android SDK 36 |
| Linguaggio | Kotlin 2.2 |
| Interfaccia | Jetpack Compose (Material 3) |

Il progetto **non include il Gradle wrapper**: si usa un Gradle 9.3.1 già installato.

---

## Compilare

```bash
~/.gradle/wrapper/dists/gradle-9.3.1-bin/codex/gradle-9.3.1/bin/gradle --offline :app:assembleDebug
```

L'APK viene prodotto in:

```
app/build/outputs/apk/debug/app-debug.apk
```

### Installare sul telefono

1. Abilita "Installa da origini sconosciute" per il file manager/browser che usi.
2. **Disinstalla la versione precedente** se era firmata con una chiave diversa: l'APK di debug usa `debug.keystore`, quindi firme diverse bloccano l'aggiornamento.
3. Apri l'APK e installa.

---

## Come funziona

### 1. Estrazione dei flussi (`YouTubeStreamService`)

L'app interroga l'endpoint InnerTube `POST https://www.youtube.com/youtubei/v1/player` e riceve gli URL reali dei flussi (`streamingData`), poi li filtra per formato, risoluzione e bitrate.

Due aspetti sono indispensabili perché YouTube non risponda con il controllo anti-bot:

- un **`visitorData` valido**, letto da `responseContext.visitorData` della risposta del player (con la pagina web come ripiego);
- un **client riconosciuto**. L'app usa in ordine:

| Ordine | Client | Note |
|---|---|---|
| 1 | `ANDROID_VR 1.65.10` | restituisce formati adattivi (1080p, audio separato); sotto blocco CDN dal 17/08/2026 |
| 2 | `ANDROID_VR 1.62.27` | stessa resa, versione precedente |
| 3 | `ANDROID 21.26.364` | ripiego: solo flusso progressivo (video+audio), sempre scaricabile |

### 2. Download dei byte (`MediaFileGenerator`)

- Il CDN di YouTube lega l'URL all'indirizzo IP che l'ha richiesto: l'app scarica con **Range request**, mostra byte trasferiti e velocità, e in caso di `403` ripete il trasferimento forzando prima IPv4 e poi IPv6.
- Se il `403` resta, **cambia client** e rifà l'estrazione: il blocco è per client, non per dispositivo.
- **Audio**: scarica la traccia AAC dedicata; se il client offre solo il flusso progressivo, estrae la traccia audio in M4A con `MediaExtractor`/`MediaMuxer`.
- **Video**: scarica traccia video e traccia audio e le unisce con `MediaMuxer` senza ricodifica; se non è possibile, salva il flusso progressivo (in genere 360p).
- Salvataggio su cartella scelta (SAF) o in `Music`/`Movies`, con pulizia dei file temporanei.

### 3. Interfaccia e stato

`TubeConvertViewModel` gestisce la coda, aggiorna lo stato delle attività e salva i download completati nel database Room, così la libreria resta disponibile anche dopo il riavvio.

---

## Struttura del progetto

```
app/src/main/java/com/example/
├── MainActivity.kt                  # punto di ingresso, navigazione tra le schermate
├── data/                            # Room: entity, DAO, database, preferenze cartella
├── model/MediaModels.kt             # formati, qualità, stato e modello delle attività
├── player/AudioPlayerManager.kt     # riproduzione audio nella libreria
├── service/
│   ├── MediaFileGenerator.kt        # estrazione flussi, download, mux, salvataggio
│   └── YouTubeParser.kt             # metadati del video (titolo, autore, miniatura)
├── ui/
│   ├── screens/                     # ConverterScreen, LibraryScreen, SettingsScreen
│   ├── components/                  # card attività, card download, player, dialog video
│   └── theme/                       # colori, tipografia, tema
├── utils/FileUtils.kt               # nomi file, dimensioni leggibili, MIME
└── viewmodel/TubeConvertViewModel.kt
```

---

## Limiti noti

- **Nessun supporto a MP3**: l'audio è AAC in M4A (vedi sopra).
- **Nessun login**: non vengono usati cookie di un account. Se YouTube blocca l'indirizzo IP della rete, tutti i client possono fallire; in quel caso serve cambiare rete o aggiungere i cookie di un account.
- **Video "made for kids"** non sono disponibili con il client `ANDROID_VR`.
- Un `403` può durare qualche minuto: l'app riprova cambiando client e famiglia di indirizzi, ma non può aggirare un blocco a livello di rete.

---

## Risoluzione dei problemi

| Sintomo | Causa e rimedio |
|---|---|
| "YouTube ha bloccato il download (403)" | Blocco lato CDN/IP. Attendi qualche minuto, disattiva la VPN, prova un'altra rete. |
| "Sign in to confirm you're not a bot" | Controllo anti-bot: riprova più tardi o da un'altra rete. |
| L'app si installa ma non aggiorna la precedente | Firma diversa: disinstalla la vecchia versione. |
| Il file audio non si apre in un player | Verifica che sia `.m4a`: i vecchi download `.mp3` erano in realtà WebM/Opus rinominati. |
| Nessun file in Galleria | I file finiscono in `Music`/`Movies` dell'app; controlla la cartella scelta nelle impostazioni. |

---

## Note legali

Il download di contenuti protetti può violare i Termini di servizio di YouTube e il diritto d'autore. Usa l'app solo per contenuti di cui hai i diritti o che ne consentono il download, e per uso personale.

---

## Licenza

Progetto dimostrativo a scopo didattico. Nessuna affiliazione con YouTube o Google.
