package com.blackcode.poscandykush

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class InvoiceAdapter(private var invoices: MutableList<Invoice>) :
    RecyclerView.Adapter<InvoiceAdapter.InvoiceViewHolder>() {

    interface OnInvoiceClickListener {
        fun onInvoiceClick(invoice: Invoice)
    }

    private var clickListener: OnInvoiceClickListener? = null

    fun setOnInvoiceClickListener(listener: OnInvoiceClickListener) {
        clickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InvoiceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invoice_row, parent, false)
        return InvoiceViewHolder(view)
    }

    override fun onBindViewHolder(holder: InvoiceViewHolder, position: Int) {
        val invoice = invoices[position]
        holder.bind(invoice)
    }

    override fun getItemCount(): Int = invoices.size

    fun updateInvoices(newInvoices: List<Invoice>) {
        android.util.Log.d("InvoiceAdapter", "updateInvoices called with ${newInvoices.size} invoices")
        invoices.clear()
        invoices.addAll(newInvoices)
        notifyDataSetChanged()
        android.util.Log.d("InvoiceAdapter", "Adapter now has ${invoices.size} items")
    }

    inner class InvoiceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvInvoiceNumber: TextView = itemView.findViewById(R.id.tv_invoice_number)
        private val tvInvoiceDate: TextView = itemView.findViewById(R.id.tv_invoice_date)
        private val tvDueDate: TextView = itemView.findViewById(R.id.tv_due_date)
        private val tvCustomerName: TextView = itemView.findViewById(R.id.tv_customer_name)
        private val tvItemsCount: TextView = itemView.findViewById(R.id.tv_items_count)
        private val tvTotalAmount: TextView = itemView.findViewById(R.id.tv_total_amount)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    clickListener?.onInvoiceClick(invoices[position])
                }
            }
        }

        fun bind(invoice: Invoice) {
            tvInvoiceNumber.text = "Invoice ${invoice.number}"
            tvInvoiceDate.text = invoice.date
            tvDueDate.text = invoice.dueDate?.let { "Due: $it" } ?: "No due date"
            tvCustomerName.text = "Customer: ${invoice.customerName}"
            tvItemsCount.text = "Items: ${invoice.getItemsCount()}"
            tvTotalAmount.text = invoice.getFormattedTotal()
        }
    }
}
