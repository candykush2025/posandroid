package com.blackcode.poscandykush

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class InvoiceItemAdapter(
    private val items: MutableList<InvoiceItem>,
    private val onItemChanged: () -> Unit,
    private val isEditable: Boolean = true
) : RecyclerView.Adapter<InvoiceItemAdapter.InvoiceItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InvoiceItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invoice_item, parent, false)
        return InvoiceItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: InvoiceItemViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, onItemChanged, isEditable)
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<InvoiceItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class InvoiceItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvProductName: TextView = itemView.findViewById(R.id.tv_product_name)
        private val etQuantity: EditText = itemView.findViewById(R.id.et_quantity)
        private val etPrice: EditText = itemView.findViewById(R.id.et_price)
        private val tvItemTotal: TextView = itemView.findViewById(R.id.tv_item_total)

        fun bind(item: InvoiceItem, onItemChanged: () -> Unit, isEditable: Boolean) {
            tvProductName.text = item.productName
            etQuantity.setText(item.quantity.toString())
            etPrice.setText(item.price.toString())
            updateTotal(item)

            etQuantity.isEnabled = isEditable
            etPrice.isEnabled = isEditable

            // Only add text watchers if editable (for AddInvoiceActivity)
            if (isEditable) {
                etQuantity.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        val qty = s?.toString()?.toDoubleOrNull() ?: 1.0
                        item.quantity = qty
                        item.total = qty * item.price // Update total
                        updateTotal(item)
                        onItemChanged()
                    }
                })

                etPrice.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        val newPrice = s?.toString()?.toDoubleOrNull() ?: 0.0
                        // Can't reassign price directly anymore, would need to recreate item
                        // For now, just update the total based on current values
                        item.quantity = item.quantity // Keep quantity same
                        item.total = item.quantity * newPrice // Update total
                        updateTotal(item)
                        onItemChanged()
                    }
                })
            }
        }

        private fun updateTotal(item: InvoiceItem) {
            tvItemTotal.text = "Total: ${item.getFormattedTotal()}"
        }
    }
}
