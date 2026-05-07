package cl.localplay

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import cl.localplay.databinding.ActivityMainBinding
import coil.load
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var controller: MediaController? = null
    private val tracks = mutableListOf<Track>()
    private lateinit var adapter: MusicAdapter

    // Handler para actualizar la barra de progreso cada 500ms
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            progressHandler.postDelayed(this, 500)
        }
    }

    // ── Permiso de lectura de audio ─────────────────────────────────────────
    private val audioPermission: String get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) initApp()
        else Toast.makeText(this, "Se necesita permiso para leer música", Toast.LENGTH_LONG).show()
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()

        if (ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            initApp()
        } else {
            permissionLauncher.launch(audioPermission)
        }
    }

    override fun onStart() {
        super.onStart()
        connectToService()
    }

    override fun onStop() {
        progressHandler.removeCallbacks(progressRunnable)
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        super.onStop()
    }

    // ── UI setup ────────────────────────────────────────────────────────────
    private fun setupUI() {
        adapter = MusicAdapter { _, index ->
            controller?.apply {
                seekToDefaultPosition(index)
                play()
            }
        }
        binding.recyclerPlaylist.apply {
            this.adapter = this@MainActivity.adapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            setHasFixedSize(true)
        }

        binding.btnPlay.setOnClickListener {
            controller?.let { if (it.isPlaying) it.pause() else it.play() }
        }
        binding.btnPrev.setOnClickListener {
            controller?.let {
                if (it.currentPosition > 3000L) it.seekTo(0L)
                else it.seekToPreviousMediaItem()
            }
        }
        binding.btnNext.setOnClickListener { controller?.seekToNextMediaItem() }

        binding.btnShuffle.setOnClickListener {
            controller?.let {
                it.shuffleModeEnabled = !it.shuffleModeEnabled
                binding.btnShuffle.isActivated = it.shuffleModeEnabled
            }
        }

        binding.btnRepeat.setOnClickListener {
            controller?.let { ctrl ->
                ctrl.repeatMode = when (ctrl.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else                   -> Player.REPEAT_MODE_OFF
                }
                updateRepeatButton(ctrl.repeatMode)
            }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = controller?.duration ?: 0L
                    if (duration > 0) {
                        binding.textCurrent.text = formatTime((progress / 100.0 * duration).toLong())
                    }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {
                progressHandler.removeCallbacks(progressRunnable)
            }
            override fun onStopTrackingTouch(sb: SeekBar) {
                val duration = controller?.duration ?: 0L
                if (duration > 0) {
                    controller?.seekTo((sb.progress / 100.0 * duration).toLong())
                }
                progressHandler.post(progressRunnable)
            }
        })

        binding.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) controller?.setDeviceVolume(progress, 0)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    // ── Inicializar app: cargar pistas + conectar al servicio ───────────────
    private fun initApp() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { scanMediaStore() }
            tracks.clear()
            tracks.addAll(loaded)
            adapter.submitList(tracks.toList())
            binding.textTrackCount.text = "${tracks.size} canciones"
        }
    }

    // ── Conectar al MusicService vía MediaController ────────────────────────
    private fun connectToService() {
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            try {
                controller = future.get()
                onControllerReady()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun onControllerReady() {
        val ctrl = controller ?: return

        // Cargar todas las pistas en el player
        val mediaItems = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(track.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(track.artUri)
                        .build()
                )
                .build()
        }

        if (mediaItems.isNotEmpty() && ctrl.mediaItemCount == 0) {
            ctrl.setMediaItems(mediaItems)
            ctrl.prepare()
        }

        // Restaurar estado de shuffle/repeat al reconectar
        binding.btnShuffle.isActivated = ctrl.shuffleModeEnabled
        updateRepeatButton(ctrl.repeatMode)

        ctrl.addListener(playerListener)
        updateUIFromPlayer()
        progressHandler.post(progressRunnable)
    }

    // ── Player.Listener — actualiza la UI cuando cambia el estado ──────────
    private val playerListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            binding.btnPlay.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
            if (isPlaying) progressHandler.post(progressRunnable)
            else progressHandler.removeCallbacks(progressRunnable)
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            updateCurrentTrack()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            binding.btnShuffle.isActivated = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            updateRepeatButton(repeatMode)
        }
    }

    // ── Actualizar UI ───────────────────────────────────────────────────────
    private fun updateUIFromPlayer() {
        val ctrl = controller ?: return
        binding.btnPlay.setImageResource(
            if (ctrl.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        updateCurrentTrack()
    }

    private fun updateCurrentTrack() {
        val ctrl = controller ?: return
        val idx  = ctrl.currentMediaItemIndex
        if (idx < 0 || idx >= tracks.size) return

        val track = tracks[idx]
        binding.textTitle.text  = track.title
        binding.textArtist.text = track.artist.ifEmpty { track.album.ifEmpty { "Sin metadatos" } }
        binding.textTotal.text  = track.durationFormatted

        // Carátula del álbum con Coil
        binding.imageCover.load(track.artUri) {
            placeholder(R.drawable.cover_placeholder)
            error(R.drawable.cover_placeholder)
            crossfade(200)
        }

        // Resaltar pista en la lista
        adapter.nowPlayingIndex = idx

        // Scroll para mostrar la pista actual
        (binding.recyclerPlaylist.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(idx, 0)
    }

    private fun updateProgress() {
        val ctrl = controller ?: return
        val duration = ctrl.duration
        val position = ctrl.currentPosition
        if (duration > 0) {
            binding.seekBar.progress = ((position.toDouble() / duration) * 100).toInt()
            binding.textCurrent.text = formatTime(position)
        }
    }

    private fun updateRepeatButton(mode: Int) {
        binding.btnRepeat.isActivated = mode != Player.REPEAT_MODE_OFF
        binding.btnRepeat.setImageResource(
            when (mode) {
                Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                else                   -> R.drawable.ic_repeat
            }
        )
    }

    // ── Escanear MediaStore para encontrar música ───────────────────────────
    private fun scanMediaStore(): List<Track> {
        val result = mutableListOf<Track>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 30000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, sortOrder
        )?.use { cursor ->
            val idCol      = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id      = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)

                val artUri = Uri.parse("content://media/external/audio/albumart/$albumId")
                val trackUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )

                result.add(Track(
                    id       = id,
                    title    = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Sin título",
                    artist   = cursor.getString(artistCol)
                                 ?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "",
                    album    = cursor.getString(albumCol)
                                 ?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "",
                    uri      = trackUri,
                    duration = cursor.getLong(durCol),
                    artUri   = artUri
                ))
            }
        }
        return result
    }

    // ── Utilidades ──────────────────────────────────────────────────────────
    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }
}
