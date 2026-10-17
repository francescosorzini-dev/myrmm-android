# myrmm per Android

App che fa segnalare un telefono Android alla console **myrmm** (inventario, stato online, comandi di blocco schermo e ripristino). Non usa Google né l'Android Management API: nessuna quota, nessun account aziendale.

- **Si installa a mano** (APK dalla pagina *Releases* → `latest`), consentendo le app da fonti sconosciute.
- **Si configura** aprendo il link `myrmm://setup?…` (o la sua scheda QR) che la console genera nella pagina *Piattaforma → Android → Alternativa senza Google*, oppure a mano: indirizzo `https://…/index.php`, token del cliente, nome, intervallo.
- **Cosa invia**: modello, produttore, versione di Android e livello patch di sicurezza, batteria, spazio, indirizzo IP, stato dell'amministratore.
- **Cosa riceve**: comandi che la console mette in coda (blocca schermo, ripristino, ping). Il telefono li esegue al check-in successivo (al massimo ogni intervallo; minimo 15 minuti, limite di Android per i lavori periodici).
- **Blocco e ripristino** richiedono di attivare nell'app l'*amministratore del dispositivo*; il ripristino richiede anche la spunta «Consenti il ripristino». Senza, i comandi falliscono e la console lo mostra.
- **Sicurezza**: solo HTTPS; il token è del cliente, l'identificativo del telefono è casuale e resta sul telefono.

## Compilazione

Il workflow `.github/workflows/build.yml` compila a ogni push su `main` e pubblica `myrmm-android.apk` nella release `latest`. Se la compilazione fallisce, gli errori compaiono come annotazioni del run e il log completo è nell'artefatto `build-log`.

La firma usa la chiave di debug del runner, tenuta in cache: se la cache scade, l'APK successivo avrà un'altra firma e va installato dopo aver disinstallato il precedente.

## Aggiornamenti e firma stabile

L'app mostra «Aggiornamento disponibile» quando il server pubblica una versione più recente (file `apk-version.txt` accanto all'APK). L'installazione va confermata a mano sul telefono e riesce solo se la firma è la stessa dell'APK installato.

Per una firma che non cambia mai, genera la chiave una sola volta sul tuo computer e conservala:

```
keytool -genkeypair -v -keystore myrmm.jks -alias myrmm -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 myrmm.jks   # incolla il risultato nel segreto KEYSTORE_B64
```

Nelle impostazioni del repository (Settings → Secrets and variables → Actions) crea `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD`. Il workflow li usa se presenti; altrimenti ricade sulla chiave di debug in cache. Non pubblicare mai il file `.jks`.
