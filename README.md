# Clankyard

Clankyard è un’officina di sviluppo nativa per Android, pensata per telefoni
e tablet. Permette di importare un progetto in uno spazio di lavoro privato,
esplorare i file, modificarli, eseguire comandi locali e usare un assistente AI
opzionale per proporre modifiche revisionabili.

È scritta in Kotlin con Jetpack Compose e Material 3. Non è un clone di VS
Code, non usa WebView e non è Flutter. La mascotte è disponibile in
[`icon.png`](icon.png).

## Stato attuale

Versione `0.1.0`, ancora pre-1.0. L’app è funzionante come workshop locale, ma
alcune integrazioni restano sperimentali. Il supporto alla compilazione Android
direttamente sul dispositivo è presente nei moduli `:build:api`,
`:build:runtime` e `:build:engine`, ma non è ancora collegato al flusso UI
principale dell’app.

## Funzioni disponibili

- onboarding in quattro passaggi: introduzione, scelta della directory,
  collegamento Nostr tramite Amber e configurazione AI;
- workspace copiati nello storage privato dell’app tramite Storage Access
  Framework, con esportazione esplicita del progetto;
- file manager con apertura, creazione, rinomina, eliminazione, salvataggio e
  refresh;
- editor basato su sora-editor per Kotlin, Java, Python, JavaScript/TypeScript,
  TSX/JSX, JSON, Markdown, Bash, C e C++; YAML viene aperto come testo
  semplice;
- ricerca nel progetto, palette file e command palette;
- shell locale `/system/bin/sh` confinata alla directory del workspace scelto.
  Non è una distribuzione Linux, non offre una PTY e non esegue binari arbitrari;
- esecuzione SSH opzionale con password e verifica host-key TOFU;
- Git locale: init, status, diff, stage, unstage e commit. Clone e push non
  sono ancora disponibili;
- Clanker opzionale con modalità ASK/PLAN/EDIT, contesto filtrato e patch da
  accettare o rifiutare. Non applica modifiche né commit automaticamente;
- provider AI OpenAI, Anthropic, xAI, Ollama e host OpenAI-compatible;
- login Nostr tramite Amber/NIP-55 o bunker NIP-46. La chiave privata `nsec`
  non entra nell’app;
- cinque temi con accenti e gradienti coordinati: Rust, Terminal, Nostr,
  Firered e Deepsea;
- layout adattivo: su schermi landscape il workspace parte con file manager,
  editor e chat AI aperti in tre pannelli.

## Privacy e sicurezza

Clankyard non ha un backend proprietario e non raccoglie le API key. Le chiavi
sono protette da Android Keystore; il backup dell’app è disabilitato per
credenziali, bozze, journal e workspace. Il filtro dei segreti viene applicato
prima di inviare contesto ai provider AI.

L’AI è BYOK e opzionale. Una chiave salvata su un dispositivo rootato o
compromesso può comunque essere utilizzata: configura i limiti di spesa presso
il provider. Per la policy completa consulta [`SECURITY.md`](SECURITY.md).

## Build da sorgente

Requisiti: JDK 17 e Android SDK 36.

```bash
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew :app:assembleDebug
./gradlew test :app:testDebugUnitTest :app:licenseCheck
```

Test strumentali, con dispositivo o emulatore Android:

```bash
./gradlew :app:connectedDebugAndroidTest
```

L’ABI nativa inclusa per gli esperimenti di esecuzione è `arm64-v8a`. I
moduli di build on-device sono sperimentali e richiedono runtime/toolchain
separati; non sono ancora una funzione esposta nell’interfaccia principale.

## Licenza

Il codice originale di Clankyard è distribuito con [MIT License](LICENSE).
Le dipendenze di terze parti mantengono le rispettive licenze; i dettagli e
gli avvisi sono in [`NOTICE`](NOTICE), [`LICENSES/`](LICENSES/) e
[`docs/licenses.md`](docs/licenses.md).
