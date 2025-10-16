# VLC Configuration Fix - flutter_rtp POC

## Data
2025-01-XX

## Problema
Configurazione VLC eccessivamente complessa con troppi buffer e opzioni che potevano causare artefatti video.

## Soluzione Applicata
Semplificata la configurazione VLC seguendo l'implementazione legacy funzionante da `oculus-android-vlc`.

## Modifiche al File
**File:** `android/app/src/main/kotlin/com/example/flutter_rtp/VlcPlayer.kt`

### 1. Opzioni LibVLC Semplificate (linea 35)
```kotlin
// PRIMA (troppo complesso):
val options = ArrayList<String>().apply {
    add("--rtsp-tcp")
    add("--network-caching=500")
    add("--live-caching=500")
    add("--rtsp-frame-buffer-size=1000000")
    add("--avcodec-hw=none")
    add("--avcodec-threads=0")
    // ... molte altre opzioni (50+ righe)
}

// DOPO (minimale come legacy):
val options = ArrayList<String>()
// Minimal options - matching legacy working implementation
```

### 2. Network Caching Ridotto (linea 127)
```kotlin
// PRIMA:
media.addOption(":network-caching=500")
media.addOption(":live-caching=500")
media.addOption(":rtsp-tcp")
media.addOption(":rtsp-frame-buffer-size=1000000")
media.addOption(":clock-jitter=5000")
// ... molte altre opzioni

// DOPO:
media.addOption(":network-caching=400")
// Use same options as legacy working implementation
```

### 3. Hardware Decoder Abilitato
```kotlin
// PRIMA (forzava software):
media.setHWDecoderEnabled(false, false)  // FORZA SOFTWARE DECODING

// DOPO (come legacy):
media.setHWDecoderEnabled(true, false)
```

### 4. Rimosso Configurazioni Extra
- Rimosso aspect ratio e scale manuale
- Rimosso deinterlacing forzato
- Rimosso opzioni clock-synchro e jitter
- Rimosso verbose logging (-vvv)

## Vantaggi
1. **Buffer ridotti**: da 500ms a 400ms (meno latenza)
2. **Meno opzioni conflittuali**: configurazione minimale come nel progetto funzionante
3. **Hardware decoder abilitato**: migliori performance quando disponibile
4. **Codice più pulito**: da ~70 righe di opzioni a ~5 righe

## Riferimento
- Progetto legacy funzionante: `oculus-android-vlc/ANPRTattileActivity.java`
- Stesso fix applicato a: `levis_mobile/android/app/src/main/kotlin/it/oltrematica/levis_mobile/VlcPlayer.kt`

## Risultato Atteso
Video stream più fluido e senza artefatti, con latenza ridotta.
