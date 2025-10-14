package com.example.flutter_rtp

import android.os.Bundle
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val TAG = "MainActivity"
    private val CHANNEL = "com.example.vlc/player"
    private var vlcPlayer: VlcPlayer? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        Log.d(TAG, "🔷 Configurazione FlutterEngine")
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            Log.d(TAG, "📨 Ricevuto metodo: ${call.method}")
            when (call.method) {
                "startStream" -> {
                    val url = call.argument<String>("url")
                    val width = call.argument<Int>("width") ?: 1280
                    val height = call.argument<Int>("height") ?: 1024  // Default corretto
                    
                    Log.d(TAG, "▶️ startStream chiamato")
                    Log.d(TAG, "   URL: $url")
                    Log.d(TAG, "   Dimensioni richieste: ${width}x${height}")
                    Log.d(TAG, "   Stream config: H264, 512Kbit/s, 5fps, RTSP/TCP")
                    
                    if (url != null) {
                        if (vlcPlayer == null) {
                            Log.d(TAG, "🎬 Creazione nuovo VlcPlayer")
                            vlcPlayer = VlcPlayer(this, flutterEngine)
                        } else {
                            Log.d(TAG, "♻️ Riutilizzo VlcPlayer esistente")
                        }
                        
                        try {
                            vlcPlayer?.startStream(url, width, height) { textureId ->
                                Log.d(TAG, "✅ Texture creato con ID: $textureId")
                                result.success(mapOf("textureId" to textureId))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Errore in startStream: ${e.message}", e)
                            result.error("STREAM_ERROR", "Errore avvio stream: ${e.message}", null)
                        }
                    } else {
                        Log.e(TAG, "❌ URL nullo")
                        result.error("INVALID_URL", "URL non valido", null)
                    }
                }
                "stopStream" -> {
                    Log.d(TAG, "⏹️ stopStream chiamato")
                    vlcPlayer?.stopStream()
                    result.success(null)
                }
                else -> {
                    Log.w(TAG, "⚠️ Metodo non implementato: ${call.method}")
                    result.notImplemented()
                }
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "🔴 MainActivity onDestroy")
        vlcPlayer?.release()
        super.onDestroy()
    }
}
