package com.my8a.gestureapplock.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.my8a.gestureapplock.data.GestureStore
import com.my8a.gestureapplock.data.InstalledApp
import com.my8a.gestureapplock.databinding.ItemAppBinding
import java.util.Locale

class AppListAdapter(
    apps: List<InstalledApp>,
    private val listener: OnAppActionListener
) : RecyclerView.Adapter<AppListAdapter.VH>() {

    interface OnAppActionListener {
        fun onSetGesture(app: InstalledApp)
        fun onRemoveGesture(app: InstalledApp)
        fun onToggleLock(app: InstalledApp, enable: Boolean)
    }

    // Full source list (unchanged) and the list currently displayed
    private val allApps = ArrayList<InstalledApp>(apps)
    private val displayApps = ArrayList<InstalledApp>(apps)

    inner class VH(private val b: ItemAppBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(app: InstalledApp) {
            b.appIcon.setImageDrawable(app.icon)
            b.appLabel.text = app.label

            val samples = GestureStore.getSamplesCount(b.root.context, app.packageName)
            b.sampleCount.text = if (samples > 0) "Samples: $samples" else "No samples"

            b.lockSwitch.setOnCheckedChangeListener(null)
            b.lockSwitch.isChecked = app.locked
            b.lockSwitch.setOnCheckedChangeListener { _, isChecked ->
                listener.onToggleLock(app, isChecked)
            }

            b.btnSetGesture.setOnClickListener { listener.onSetGesture(app) }
            b.btnRemoveGesture.setOnClickListener { listener.onRemoveGesture(app) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemAppBinding.inflate(inflater, parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(displayApps[position])
    }

    override fun getItemCount(): Int = displayApps.size

    // update both lists when MainActivity reloads apps
    fun updateApps(newApps: List<InstalledApp>) {
        allApps.clear()
        allApps.addAll(newApps)
        // apply previous filter (if any) by clearing displayApps and copying allApps
        displayApps.clear()
        displayApps.addAll(allApps)
        notifyDataSetChanged()
    }

    // filter by query (case-insensitive, matches label or package)
    fun filter(query: String?) {
        val q = query?.trim()?.lowercase(Locale.getDefault()) ?: ""
        displayApps.clear()
        if (q.isEmpty()) {
            displayApps.addAll(allApps)
        } else {
            for (a in allApps) {
                if (a.label.lowercase(Locale.getDefault()).contains(q) ||
                    a.packageName.lowercase(Locale.getDefault()).contains(q)) {
                    displayApps.add(a)
                }
            }
        }
        notifyDataSetChanged()
    }
}
