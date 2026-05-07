package cl.localplay

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Servicio de reproducción en foreground.
 *
 * Media3 se encarga de:
 *   • Notificación persistente con controles (play/pause/prev/next)
 *   • Integración con auriculares, Bluetooth, Android Auto
 *   • Pantalla de bloqueo con info de pista y controles
 *   • Botones físicos de media del celular (teclas laterales)
 *   • Foco de audio (pausa automática si llega una llamada)
 *
 * Este servicio NO tiene permiso INTERNET → nunca puede conectarse
 * a ningún servidor, independientemente del código.
 */
@UnstableApi
class MusicService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()

        // Configuración de audio: tipo MÚSICA, con manejo automático del foco
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)  // pausa si se desconectan auriculares
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .build()
    }

    /**
     * MediaSessionService llama esto cada vez que un controlador
     * (MainActivity) quiere conectarse. Devolvemos nuestra sesión.
     */
    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession = mediaSession

    /**
     * Si el usuario cierra la app desde recientes y no hay reproducción
     * activa, detenemos el servicio para no consumir recursos.
     */
    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }
}
