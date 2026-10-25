package com.example.tracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tracker.R
import com.example.tracker.data.TrackSession
import com.example.tracker.util.TrackStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionAdapter(
    private val onClick: (TrackSession) -> Unit,
    private val onShare: (TrackSession) -> Unit,
    private val onLongClick: (TrackSession) -> Unit
) : RecyclerView.Adapter<SessionAdapter.VH>() {

    data class Row(val session: TrackSession, val stats: TrackStats.Stats)

    private val items = mutableListOf<Row>()
    private val dateFmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru", "RU"))

    fun submit(list: List<Row>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val name: TextView = v.findViewById(R.id.tvName)
        private val meta: TextView = v.findViewById(R.id.tvMeta)
        private val share: View = v.findViewById(R.id.btnShare)

        fun bind(row: Row) {
            val st = row.stats
            name.text = row.session.name
            val date = dateFmt.format(Date(row.session.startedAt))
            meta.text = "$date · ${TrackStats.distance(st.distanceM)} · " +
                "${TrackStats.duration(st.durationMs)} · ${st.points} точек"
            itemView.setOnClickListener { onClick(row.session) }
            itemView.setOnLongClickListener { onLongClick(row.session); true }
            share.setOnClickListener { onShare(row.session) }
        }
    }
}
