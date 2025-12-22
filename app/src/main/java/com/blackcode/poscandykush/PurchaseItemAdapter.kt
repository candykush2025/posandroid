package com.blackcode.poscandykush

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PurchaseItemAdapter(
    private val items: MutableList<PurchaseItem>,
    private val onItemChanged: () -> Unit,
    private val isEditable: Boolean = true
) : RecyclerView.Adapter<PurchaseItemAdapter.PurchaseItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PurchaseItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invoice_item, parent, false)
        return PurchaseItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: PurchaseItemViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, onItemChanged, isEditable)
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<PurchaseItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class PurchaseItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvProductName: TextView = itemView.findViewById(R.id.tv_product_name)
        private val etQuantity: EditText = itemView.findViewById(R.id.et_quantity)
        private val etPrice: EditText = itemView.findViewById(R.id.et_price)
        private val tvItemTotal: TextView = itemView.findViewById(R.id.tv_item_total)

        fun bind(item: PurchaseItem, onItemChanged: () -> Unit, isEditable: Boolean) {
            tvProductName.text = item.productName
            etQuantity.setText(item.quantity.toString())
            etPrice.setText(item.price.toString())
            updateTotal(item)

            etQuantity.isEnabled = isEditable
            etPrice.isEnabled = isEditable

            etQuantity.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val qty = s?.toString()?.toDoubleOrNull() ?: 1.0
                    item.quantity = qty
                    item.total = qty * item.price
                    updateTotal(item)
                    onItemChanged()
                }
            })

            etPrice.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val price = s?.toString()?.toDoubleOrNull() ?: 0.0
                    item.price = price
                    item.total = item.quantity * price
                    updateTotal(item)
                    onItemChanged()
                }
            })
        }

        private fun updateTotal(item: PurchaseItem) {
            tvItemTotal.text = "Total: ${item.getFormattedTotal()}"
        }
    }
}

