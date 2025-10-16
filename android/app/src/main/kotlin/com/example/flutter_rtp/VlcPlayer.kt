package com.example.flutter_rtp

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.util.Log
import android.view.Surface
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.TextureRegistry
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout

class VlcPlayer(
    private val activity: FlutterActivity,
    private val flutterEngine: FlutterEngine
) {
    private val TAG = "VlcPlayer"
    private val CHANNEL = "com.example.vlc/player"
    
    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var textureEntry: TextureRegistry.SurfaceTextureEntry? = null
    private var surface: Surface? = null
    private val methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)

    init {
        Log.d(TAG, "🎬 Inizializzazione VlcPlayer")
        try {
            val options = ArrayList<String>().apply {
                // Opzioni ottimizzate per streaming RTSP IP camera con minimo buffering
                add("--rtsp-tcp")                        // TCP obbligatorio per stabilità con telecamere
                add("--network-caching=150")             // Cache minima (150ms) per latenza ultra-bassa
                add("--live-caching=150")                // Cache live stream ultra-ridotta
                add("--rtsp-frame-buffer-size=500000")   // Buffer frame RTSP aumentato per H264 1280x1024
                
                // Gestione frame persi e buffering - priorità fluidità
                add("--drop-late-frames")                // Scarta frame in ritardo - ESSENZIALE
                add("--skip-frames")                     // Salta frame se CPU sovraccarica
                add("--avcodec-skip-frame=0")            // Non saltare frame nel decoder (0=none)
                add("--avcodec-skip-idct=0")             // Non saltare IDCT (0=none)
                add("--avcodec-skiploopfilter=4")        // Skip loop filter (4=all) per max performance
                
                // Decodifica veloce e hardware acceleration
                add("--avcodec-fast")                    // Fast decoding - minor qualità, max velocità
                add("--avcodec-hw=any")                  // HW acceleration se disponibile
                add("--avcodec-threads=0")               // Auto thread count per decoder
                add("--codec=avcodec,all")               // Priorità avcodec per H264
                
                // Clock e sincronizzazione - riduce latenza
                add("--clock-jitter=0")                  // No jitter compensation
                add("--clock-synchro=0")                 // Disabilita clock sync (0=no sync)
                add("--no-audio")                        // No audio (telecamera solo video)
                add("--no-sout-audio")                   // No stream output audio
                
                // Ottimizzazioni varie per performance
                add("--file-caching=300")                // File caching ridotto
                add("--no-video-title-show")             // No overlay titolo
                add("--no-snapshot-preview")             // No preview snapshot
                add("--no-stats")                        // No statistiche per performance
                add("--avi-index=0")                     // No index building
                add("--no-osd")                          // No on-screen display
                add("--no-keyboard-events")              // No eventi tastiera
                add("--no-mouse-events")                 // No eventi mouse
                
                // Logging per debug
                add("-vv")                               // Verbose logging
            }
            Log.d(TAG, "📋 Opzioni VLC ottimizzate per efficienza:")
            options.forEach { Log.d(TAG, "   $it") }
            
            libVlc = LibVLC(activity, options)
            Log.d(TAG, "✅ LibVLC creato")
            
            mediaPlayer = MediaPlayer(libVlc)
            Log.d(TAG, "✅ MediaPlayer creato")
            
            // Setup event listener
            mediaPlayer?.setEventListener { event ->
                Log.d(TAG, "🎵 VLC Event: type=${event.type}, buffering=${event.buffering}")
                when (event.type) {
                    MediaPlayer.Event.Stopped -> {
                        Log.w(TAG, "⏹️ MediaPlayer STOPPED")
                        notifyPlayerEvent("stopped")
                    }
                    MediaPlayer.Event.EndReached -> {
                        Log.w(TAG, "🏁 MediaPlayer END REACHED")
                        notifyPlayerEvent("ended")
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        Log.e(TAG, "❌ MediaPlayer ENCOUNTERED ERROR")
                        notifyPlayerEvent("error")
                    }
                    MediaPlayer.Event.Playing -> {
                        Log.i(TAG, "▶️ MediaPlayer PLAYING")
                        notifyPlayerEvent("playing")
                    }
                    MediaPlayer.Event.Buffering -> {
                        Log.i(TAG, "⏳ MediaPlayer BUFFERING: ${event.buffering}%")
                    }
                    MediaPlayer.Event.Opening -> {
                        Log.i(TAG, "🔓 MediaPlayer OPENING")
                    }
                    MediaPlayer.Event.Paused -> {
                        Log.i(TAG, "⏸️ MediaPlayer PAUSED")
                    }
                    MediaPlayer.Event.TimeChanged -> {
                        // Troppo verbose, commentato
                        // Log.v(TAG, "⏱️ MediaPlayer TIME CHANGED: ${event.timeChanged}")
                    }
                    else -> {
                        Log.d(TAG, "📺 MediaPlayer event: ${event.type}")
                    }
                }
            }
            Log.d(TAG, "✅ Event listener configurato")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore inizializzazione VLC", e)
        }
    }

    fun startStream(url: String, width: Int, height: Int, callback: (Long) -> Unit) {
        Log.i(TAG, "▶️ ===== START STREAM =====")
        Log.i(TAG, "   URL: $url")
        Log.i(TAG, "   Size: ${width}x${height}")
        try {
            // Create texture
            Log.d(TAG, "🖼️ Creazione texture...")
            val registry = flutterEngine.renderer
            textureEntry = registry.createSurfaceTexture()
            val surfaceTexture = textureEntry!!.surfaceTexture()
            surfaceTexture.setDefaultBufferSize(width, height)
            Log.d(TAG, "✅ Texture creato con ID: ${textureEntry!!.id()}")
            surface = Surface(surfaceTexture)
            Log.d(TAG, "✅ Surface creato")

            // Setup VLC output con aspect ratio automatico
            Log.d(TAG, "📺 Setup VLC output...")
            val vout: IVLCVout = mediaPlayer!!.vlcVout
            Log.d(TAG, "   Detach views precedenti...")
            vout.detachViews()
            Log.d(TAG, "   Set video surface...")
            vout.setVideoSurface(surface, null)
            Log.d(TAG, "   Set window size...")
            vout.setWindowSize(width, height)
            Log.d(TAG, "   Attach views...")
            vout.attachViews()
            Log.d(TAG, "✅ VLC output configurato")
            
            // Configura aspect ratio e scaling
            mediaPlayer?.apply {
                // Mantiene proporzioni originali del video
                aspectRatio = null  // Automatico
                scale = 0f  // Automatico, best fit
                Log.d(TAG, "✅ Aspect ratio e scale impostati su automatico")
            }

            // Create and configure media
            Log.d(TAG, "🎬 Creazione Media per URL: $url")
            val media = Media(libVlc, Uri.parse(url))
            
            Log.d(TAG, "   Abilita HW decoder...")
            media.setHWDecoderEnabled(true, false)
            
            Log.d(TAG, "   Aggiungi opzioni Media ottimizzate per telecamera RTSP:")
            // Opzioni Media specifiche - override globali per massima efficienza
            media.addOption(":network-caching=150")            // Ultra-low latency (150ms)
            media.addOption(":live-caching=150")               // Live cache ultra-bassa
            media.addOption(":rtsp-tcp")                       // TCP per stabilità
            media.addOption(":rtsp-frame-buffer-size=500000")  // Buffer adeguato per 1280x1024 H264
            media.addOption(":clock-jitter=0")                 // No jitter
            media.addOption(":clock-synchro=0")                // No clock sync
            media.addOption(":drop-late-frames")               // Scarta frame ritardo
            media.addOption(":skip-frames")                    // Skip frames se necessario
            media.addOption(":avcodec-skiploopfilter=4")       // Skip loop filter (all)
            media.addOption(":avcodec-skip-frame=0")           // No frame skip decoder
            media.addOption(":avcodec-skip-idct=0")            // No IDCT skip
            media.addOption(":avcodec-fast")                   // Fast decoding
            media.addOption(":avcodec-threads=0")              // Auto threads
            media.addOption(":no-audio")                       // No audio
            media.addOption(":file-caching=300")               // File cache ridotta
            media.addOption(":vout=android_display")           // Output diretto Android
            media.addOption(":aspect-ratio=")                  // Aspect ratio automatico
            
            Log.d(TAG, "✅ Media configurato per efficienza massima")

            // Set media and play
            Log.d(TAG, "▶️ Avvio riproduzione...")
            mediaPlayer?.media = media
            media.release()
            Log.d(TAG, "   Media rilasciato")
            
            mediaPlayer?.play()
            Log.i(TAG, "   Play() chiamato")

            Log.i(TAG, "📊 Stato MediaPlayer dopo play:")
            Log.i(TAG, "   isPlaying: ${mediaPlayer?.isPlaying}")
            Log.i(TAG, "   length: ${mediaPlayer?.length}")
            Log.i(TAG, "   time: ${mediaPlayer?.time}")
            
            Log.i(TAG, "✅ Stream started: $url")
            Log.i(TAG, "📤 Callback con textureId: ${textureEntry!!.id()}")
            callback(textureEntry!!.id())
            Log.i(TAG, "===== START STREAM COMPLETATO =====")
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERRORE CRITICO in startStream", e)
            Log.e(TAG, "   Message: ${e.message}")
            Log.e(TAG, "   Stack trace:", e)
        }
    }

    fun stopStream() {
        Log.i(TAG, "⏹️ ===== STOP STREAM =====")
        try {
            Log.d(TAG, "   Stopping mediaPlayer...")
            mediaPlayer?.stop()
            Log.d(TAG, "   Detaching VLC views...")
            mediaPlayer?.vlcVout?.detachViews()
            Log.d(TAG, "   Releasing surface...")
            surface?.release()
            surface = null
            Log.d(TAG, "   Releasing texture...")
            textureEntry?.release()
            textureEntry = null
            Log.i(TAG, "✅ Stream stopped")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping stream", e)
        }
    }

    fun release() {
        Log.i(TAG, "🔴 ===== RELEASE VLC PLAYER =====")
        try {
            Log.d(TAG, "   Chiamata stopStream()...")
            stopStream()
            Log.d(TAG, "   Releasing mediaPlayer...")
            mediaPlayer?.release()
            Log.d(TAG, "   Releasing libVlc...")
            libVlc?.release()
            mediaPlayer = null
            libVlc = null
            Log.i(TAG, "✅ VLC player released")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error releasing VLC", e)
        }
    }

    private fun notifyPlayerEvent(event: String) {
        Log.d(TAG, "📢 Notifica evento a Flutter: $event")
        try {
            methodChannel.invokeMethod("onPlayerEvent", mapOf("event" to event))
            Log.d(TAG, "✅ Evento inviato a Flutter")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore invio evento a Flutter", e)
        }
    }
}
