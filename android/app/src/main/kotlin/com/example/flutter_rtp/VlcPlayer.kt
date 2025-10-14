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
                // Opzioni ottimizzate per stream RTSP a basso frame rate (5fps)
                add("--rtsp-tcp")                    // TCP per stabilità
                add("--network-caching=1000")        // Aumentato per basso bitrate (512Kbit/s)
                add("--rtsp-frame-buffer-size=500000") // Buffer per frame H264
                add("--no-drop-late-frames")         // Non scartare frame in ritardo
                add("--no-skip-frames")              // Non saltare frame
                add("--avcodec-hw=any")             // Hardware decoding per H264
                add("--codec=avcodec,all")          // Priorità avcodec per H264
                add("--clock-jitter=5000")          // Jitter per basso frame rate
                add("--live-caching=1000")          // Cache per stream live
                add("-vvv")                          // Verbose logging
            }
            Log.d(TAG, "📋 Opzioni VLC per stream H264 5fps @ 512Kbit/s:")
            options.forEach { Log.d(TAG, "   $it") }
            
            libVlc = LibVLC(activity, options)
            Log.d(TAG, "✅ LibVLC creato")
            
            mediaPlayer = MediaPlayer(libVlc)
            Log.d(TAG, "✅ MediaPlayer creato")
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

            // Setup VLC output
            Log.d(TAG, "📺 Setup VLC output...")
            val vout: IVLCVout = mediaPlayer!!.vlcVout
            Log.d(TAG, "   Detach views precedenti...")
            vout.detachViews()
            Log.d(TAG, "   Set video surface...")
            vout.setVideoSurface(surface, null)
            Log.d(TAG, "   Attach views...")
            vout.attachViews()
            Log.d(TAG, "✅ VLC output configurato")

            // Create and configure media
            Log.d(TAG, "🎬 Creazione Media per URL: $url")
            val media = Media(libVlc, Uri.parse(url))
            
            Log.d(TAG, "   Abilita HW decoder per H264...")
            media.setHWDecoderEnabled(true, false)
            
            Log.d(TAG, "   Aggiungi opzioni specifiche per stream:")
            // Opzioni ottimizzate per questo specifico stream RTSP
            media.addOption(":network-caching=1000")      // Cache aumentata per stabilità
            media.addOption(":rtsp-tcp")                   // TCP obbligatorio
            media.addOption(":rtsp-frame-buffer-size=500000") // Buffer frame
            media.addOption(":clock-jitter=5000")          // Tolleranza jitter per 5fps
            media.addOption(":clock-synchro=0")            // Disabilita sync per basso fps
            media.addOption(":live-caching=1000")          // Cache live stream
            
            Log.d(TAG, "📊 Parametri stream attesi:")
            Log.d(TAG, "   Resolution: ${width}x${height} (1280x1024)")
            Log.d(TAG, "   Codec: H264")
            Log.d(TAG, "   Bitrate: 512 Kbit/s (CONSTANT_BITRATE)")
            Log.d(TAG, "   Frame rate: 5 fps (basso!)")
            Log.d(TAG, "   GOP: 25")
            Log.d(TAG, "   Quality: 70%")
            
            Log.d(TAG, "✅ Media configurato")

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
