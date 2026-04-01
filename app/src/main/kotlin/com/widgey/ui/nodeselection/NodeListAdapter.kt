package com.widgey.ui.nodeselection

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.widgey.data.entity.NodeEntity
import com.widgey.databinding.ItemNodeBinding

class NodeListAdapter(
    private val onNodeClick: (NodeEntity) -> Unit
) : ListAdapter<NodeEntity, NodeListAdapter.NodeViewHolder>(NodeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val binding = ItemNodeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NodeViewHolder(binding, onNodeClick)
    }

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NodeViewHolder(
        private val binding: ItemNodeBinding,
        private val onNodeClick: (NodeEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(node: NodeEntity) {
            binding.nodeName.text = node.name.ifEmpty { "(Untitled)" }
            binding.nodePreview.text = node.note?.take(100) ?: ""

            binding.root.setOnClickListener {
                onNodeClick(node)
            }
        }
    }

    private class NodeDiffCallback : DiffUtil.ItemCallback<NodeEntity>() {
        override fun areItemsTheSame(oldItem: NodeEntity, newItem: NodeEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: NodeEntity, newItem: NodeEntity): Boolean {
            return oldItem == newItem
        }
    }
}
