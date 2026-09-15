package com.stretchx.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GameAdapter(
    private val games: List<GameModel>,
    private val onGameSelected: (GameModel) -> Unit
) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {

    private var selectedIndex = 0

    init {
        if (games.isNotEmpty()) {
            games[0].isSelected = true
        }
    }

    fun getSelectedGame(): GameModel? {
        return games.getOrNull(selectedIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_game, parent, false)
        return GameViewHolder(view)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        val game = games[position]
        holder.tvName.text = game.name
        holder.tvPackage.text = game.packageName
        holder.ivIcon.setImageDrawable(game.icon)
        holder.rbSelect.isChecked = (position == selectedIndex)

        holder.itemView.setOnClickListener {
            val prev = selectedIndex
            selectedIndex = holder.bindingAdapterPosition
            games[prev].isSelected = false
            games[selectedIndex].isSelected = true
            notifyItemChanged(prev)
            notifyItemChanged(selectedIndex)
            onGameSelected(games[selectedIndex])
        }
    }

    override fun getItemCount(): Int = games.size

    class GameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.ivGameIcon)
        val tvName: TextView = itemView.findViewById(R.id.tvGameName)
        val tvPackage: TextView = itemView.findViewById(R.id.tvPackageName)
        val rbSelect: RadioButton = itemView.findViewById(R.id.rbSelectGame)
    }
}
