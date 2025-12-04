package com.borcele.pestillointeligente

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(private val logs: List<LogEntry>) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val actionTextView: TextView = itemView.findViewById(R.id.text_action)
        private val userTextView: TextView = itemView.findViewById(R.id.text_user)
        private val dateTextView: TextView = itemView.findViewById(R.id.text_date)

        fun bind(log: LogEntry) {
            actionTextView.text = "Acción: ${log.accion ?: "N/A"}"
            userTextView.text = "Usuario: ${log.usuario ?: "N/A"}"
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            dateTextView.text = if (log.fecha != null) sdf.format(log.fecha) else "No date"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    override fun getItemCount(): Int {
        return logs.size
    }
}