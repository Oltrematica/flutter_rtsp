import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'RTSP VLC Player',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const RtspPlayerPage(),
    );
  }
}

class RtspPlayerPage extends StatefulWidget {
  const RtspPlayerPage({super.key});

  @override
  State<RtspPlayerPage> createState() => _RtspPlayerPageState();
}

class _RtspPlayerPageState extends State<RtspPlayerPage> {
  static const platform = MethodChannel('com.example.vlc/player');

  bool _isPlaying = false;
  bool _isConnected = false;
  String _statusMessage = 'Pronto';

  // TextField controller per IP editabile
  final TextEditingController _ipController = TextEditingController(
    text: '10.30.173.218',
  );
  final String _rtspPort = '554';

  int _textureId = -1;

  // Getter per costruire l'URL RTSP dall'IP editabile
  String get _rtspUrl => 'rtsp://192.168.1.12:8554/mystream';

  @override
  void initState() {
    super.initState();
    print('🔷 [Flutter] Inizializzazione RtspPlayerPage');
    _setupEventChannel();
  }

  void _setupEventChannel() {
    print('🔷 [Flutter] Setup event channel per comunicazione con native');
    platform.setMethodCallHandler((call) async {
      print('🔷 [Flutter] Ricevuto evento native: ${call.method}');
      if (call.method == 'onPlayerEvent') {
        final String event = call.arguments['event'];
        print('🔷 [Flutter] Player event: $event');
        if (event == 'stopped' && _isPlaying) {
          print('⚠️ [Flutter] Stream fermato inaspettatamente');
          setState(() {
            _isPlaying = false;
            _isConnected = false;
            _statusMessage = 'Connessione persa';
          });
          _showConnectionLostDialog();
        } else if (event == 'playing') {
          print('✅ [Flutter] Stream in riproduzione');
        } else if (event == 'error') {
          print('❌ [Flutter] Errore nel player');
        }
      }
    });
  }

  Future<void> _startStream() async {
    print('▶️ [Flutter] Inizio avvio stream');
    print('▶️ [Flutter] URL: $_rtspUrl');
    print('▶️ [Flutter] Risoluzione server: 1280x1024 (High)');
    print('▶️ [Flutter] Codec: H264, Bitrate: 512Kbit/s, Frame rate: 5fps');
    try {
      setState(() {
        _statusMessage = 'Connessione in corso...';
      });

      print('📡 [Flutter] Invio comando startStream a native...');
      final result = await platform.invokeMethod('startStream', {
        'url': _rtspUrl,
        'width': 1280,
        'height': 1024, // Corretto per risoluzione server
      });

      print('✅ [Flutter] Risposta da native ricevuta: $result');
      final textureId = result['textureId'];
      print('🎬 [Flutter] Texture ID ricevuto: $textureId');

      setState(() {
        _textureId = textureId;
        _isPlaying = true;
        _isConnected = true;
        _statusMessage = 'Stream attivo';
      });

      print('✅ [Flutter] Stream avviato con successo, textureId: $_textureId');
    } on PlatformException catch (e) {
      print('❌ [Flutter] PlatformException: ${e.code} - ${e.message}');
      print('❌ [Flutter] Details: ${e.details}');
      setState(() {
        _statusMessage = 'Errore: ${e.message}';
      });
    } catch (e) {
      print('❌ [Flutter] Errore generico: $e');
      setState(() {
        _statusMessage = 'Errore: $e';
      });
    }
  }

  Future<void> _stopStream() async {
    print('⏹️ [Flutter] Inizio stop stream');
    try {
      await platform.invokeMethod('stopStream');
      print('✅ [Flutter] Stream fermato con successo');
      setState(() {
        _isPlaying = false;
        _isConnected = false;
        _textureId = -1;
        _statusMessage = 'Stream fermato';
      });
    } on PlatformException catch (e) {
      print('❌ [Flutter] Errore stop: ${e.message}');
      setState(() {
        _statusMessage = 'Errore: ${e.message}';
      });
    }
  }

  void _showConnectionLostDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Connessione Persa'),
        content: const Text('La connessione allo stream RTSP è stata persa.'),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.of(context).pop();
              _startStream();
            },
            child: const Text('Riconnetti'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Chiudi'),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _ipController.dispose();
    if (_isPlaying) {
      _stopStream();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: const Text('RTSP VLC Player'),
      ),
      body: Column(
        children: [
          // Video preview area
          Expanded(
            child: Container(
              color: Colors.black,
              child: _textureId >= 0
                  ? Texture(textureId: _textureId)
                  : Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.videocam_off,
                            size: 64,
                            color: Colors.grey[600],
                          ),
                          const SizedBox(height: 16),
                          Text(
                            'Nessuno stream attivo',
                            style: TextStyle(
                              color: Colors.grey[400],
                              fontSize: 18,
                            ),
                          ),
                        ],
                      ),
                    ),
            ),
          ),
          // Status and controls
          Container(
            padding: const EdgeInsets.all(16),
            color: Colors.white,
            child: Column(
              children: [
                // Status indicator
                Row(
                  children: [
                    Container(
                      width: 12,
                      height: 12,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: _isConnected ? Colors.green : Colors.red,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        _statusMessage,
                        style: const TextStyle(fontSize: 16),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                // IP RTSP editabile
                TextField(
                  controller: _ipController,
                  enabled: !_isPlaying, // Disabilita durante lo streaming
                  decoration: InputDecoration(
                    labelText: 'Indirizzo IP Server RTSP',
                    hintText: '10.30.173.218',
                    prefixIcon: const Icon(Icons.computer),
                    suffixText: ':$_rtspPort',
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                    filled: true,
                    fillColor: _isPlaying ? Colors.grey[100] : Colors.white,
                    helperText:
                        'Porta: $_rtspPort | Risoluzione: 1280x1024 | H264 @ 5fps',
                    helperMaxLines: 2,
                  ),
                  keyboardType: TextInputType.number,
                  inputFormatters: [
                    FilteringTextInputFormatter.allow(RegExp(r'[0-9.]')),
                  ],
                ),
                const SizedBox(height: 8),
                // URL completo preview
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 8,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.blue[50],
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: Colors.blue[200]!),
                  ),
                  child: Row(
                    children: [
                      Icon(Icons.link, size: 18, color: Colors.blue[700]),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          _rtspUrl,
                          style: TextStyle(
                            fontFamily: 'monospace',
                            fontSize: 13,
                            color: Colors.blue[900],
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                // Control buttons
                Row(
                  children: [
                    Expanded(
                      child: ElevatedButton.icon(
                        onPressed: _isPlaying ? null : _startStream,
                        icon: const Icon(Icons.play_arrow),
                        label: const Text('Avvia Lettura'),
                        style: ElevatedButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          backgroundColor: Colors.green,
                          foregroundColor: Colors.white,
                          disabledBackgroundColor: Colors.grey,
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: ElevatedButton.icon(
                        onPressed: _isPlaying ? _stopStream : null,
                        icon: const Icon(Icons.stop),
                        label: const Text('Ferma'),
                        style: ElevatedButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          backgroundColor: Colors.red,
                          foregroundColor: Colors.white,
                          disabledBackgroundColor: Colors.grey,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
