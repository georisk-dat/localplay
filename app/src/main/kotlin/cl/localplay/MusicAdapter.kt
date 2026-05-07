package cl.localplay

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cl.localplay.databinding.ItemTrackBinding

/**
 * Adapter para la lista de reproducción.
 * Usa ListAdapter + DiffUtil para actualizaciones eficientes.
 */
class MusicAdapter(
    private val onTrackClick: (Track, Int) -> Unit
) : ListAdapter<Track, MusicAdapter.TrackViewHolder>(TrackDiffCallback()) {

    /** Índice de la pista actualmente en reproducción (para resaltarla) */
    var nowPlayingIndex: Int = -1
        set(value) {
            val old = field
            field = value
            if (old >= 0) notifyItemChanged(old)
            if (value >= 0) notifyItemChanged(value)
        }

    inner class TrackViewHolder(
        private val binding: ItemTrackBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(track: Track, index: Int) {
            binding.trackNumber.text = if (index == nowPlayingIndex) "♪" else "${index + 1}"
            binding.trackTitle.text  = track.title
            binding.trackArtist.text = track.artist.ifEmpty { track.album }
            binding.trackDuration.text = track.durationFormatted

            // Resaltar pista en reproducción
            val isPlaying = index == nowPlayingIndex
            binding.root.isActivated = isPlaying
            binding.trackTitle.isActivated = isPlaying

            binding.root.setOnClickListener { onTrackClick(track, index) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val binding = ItemTrackBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }
}

private class TrackDiffCallback : DiffUtil.ItemCallback<Track>() {
    override fun areItemsTheSame(oldItem: Track, newItem: Track) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Track, newItem: Track) = oldItem == newItem
}
