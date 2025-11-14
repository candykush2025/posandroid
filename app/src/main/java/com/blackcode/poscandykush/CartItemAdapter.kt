package com.blackcode.poscandykush

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView

class CartItemAdapter : ListAdapter<CartItem, CartItemAdapter.CartItemViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_cart_display, parent, false)
        return CartItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: CartItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CartItemViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.item_name)
        private val qtyText: TextView = itemView.findViewById(R.id.item_quantity)
        private val totalText: TextView = itemView.findViewById(R.id.item_total)

        fun bind(item: CartItem) {
            nameText.text = item.name
            qtyText.text = "Qty: ${item.quantity} × $${String.format("%.2f", item.price)}"
            totalText.text = "$${String.format("%.2f", item.total)}"
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CartItem>() {
        override fun areItemsTheSame(oldItem: CartItem, newItem: CartItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CartItem, newItem: CartItem) = oldItem == newItem
    }
}