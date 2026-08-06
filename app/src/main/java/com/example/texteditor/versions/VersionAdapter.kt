package com.example.texteditor.versions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.texteditor.R
import com.example.texteditor.data.db.FileVersion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter that displays one card per snapshot.
 * The two button clicks are passed back to VersionHistoryActivity
 * through the [onViewDiff] and [onRestore] callbacks.
 */
class VersionAdapter(
    private val onViewDiff: (FileVersion) -> Unit,
    private val onRestore: (FileVersion) -> Unit
) : RecyclerView.Adapter<VersionAdapter.VersionViewHolder>() {

    private val items = mutableListOf<FileVersion>()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    fun submitList(versions: List<FileVersion>) {
        items.clear()
        items.addAll(versions)
        notifyDataSetChanged()
    }

    class VersionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.version_title)
        val date: TextView = view.findViewById(R.id.version_date)
        val storage: TextView = view.findViewById(R.id.version_storage)
        val diffButton: Button = view.findViewById(R.id.btn_view_diff)
        val restoreButton: Button = view.findViewById(R.id.btn_restore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VersionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_version, parent, false)
        return VersionViewHolder(view)
    }

    override fun onBindViewHolder(holder: VersionViewHolder, position: Int) {
        val version = items[position]
        val context = holder.itemView.context

        holder.title.text =
            context.getString(R.string.version_title_format, version.versionNumber, version.label)
        holder.date.text = dateFormat.format(Date(version.createdAt))

        // Show HOW this version is stored - nice to demonstrate the
        // "no duplication" delta storage in the viva/demo.
        holder.storage.text = if (version.baseContent != null) {
            context.getString(R.string.stored_as_base, version.baseContent.length)
        } else {
            context.getString(R.string.stored_as_delta, version.patchText?.length ?: 0)
        }

        holder.diffButton.setOnClickListener { onViewDiff(version) }
        holder.restoreButton.setOnClickListener { onRestore(version) }
    }

    override fun getItemCount(): Int = items.size
}
