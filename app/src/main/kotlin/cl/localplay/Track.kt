package cl.localplay

import android.net.Uri

/**
 * Representa una pista de audio leída desde el MediaStore del dispositivo.
 * Todos los datos provienen del dispositivo local — nunca de red.
 */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,           // content:// URI al archivo en el almacenamiento
    val duration: Long,     // milisegundos
    val artUri: Uri?        // URI a la carátula del álbum (puede ser null)
) {
    /** Duración formateada como M:SS */
    val durationFormatted: String get() {
        val totalSec = duration / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "$min:${sec.toString().padStart(2, '0')}"
    }
}
