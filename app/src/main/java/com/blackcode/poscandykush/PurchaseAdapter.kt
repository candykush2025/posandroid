package com.blackcode.poscandykush

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class PurchaseAdapter(
    private val purchases: MutableList<Purchase>
) : RecyclerView.Adapter<PurchaseAdapter.PurchaseViewHolder>() {

    private var listener: OnPurchaseClickListener? = null

    interface OnPurchaseClickListener {
        fun onPurchaseClick(purchase: Purchase)
        fun onMarkComplete(purchase: Purchase)
    }

    fun setOnPurchaseClickListener(listener: OnPurchaseClickListener) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PurchaseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_purchase, parent, false)
        return PurchaseViewHolder(view)
    }

    override fun onBindViewHolder(holder: PurchaseViewHolder, position: Int) {
        val purchase = purchases[position]
        holder.bind(purchase)
    }

    override fun getItemCount(): Int = purchases.size

    fun updatePurchases(newPurchases: List<Purchase>) {
        purchases.clear()
        purchases.addAll(newPurchases)
        notifyDataSetChanged()
    }

    inner class PurchaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cvPurchase: CardView = itemView.findViewById(R.id.cv_purchase)
        private val tvSupplierName: TextView = itemView.findViewById(R.id.tv_supplier_name)
        private val tvDate: TextView = itemView.findViewById(R.id.tv_date)
        private val tvDueDate: TextView = itemView.findViewById(R.id.tv_due_date)
        private val tvTotal: TextView = itemView.findViewById(R.id.tv_total)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_status)

        fun bind(purchase: Purchase) {
            tvSupplierName.text = purchase.supplierName
            tvDate.text = "Date: ${formatDate(purchase.date)}"
            tvDueDate.text = "Due: ${formatDate(purchase.dueDate)}"
            tvTotal.text = purchase.getFormattedTotal()
            tvStatus.text = purchase.getStatusText()

            val statusColor = when (purchase.status) {
                "completed" -> itemView.context.getColor(R.color.status_completed)
                "pending" -> itemView.context.getColor(R.color.status_pending)
                else -> itemView.context.getColor(R.color.black)
            }
            tvStatus.setTextColor(statusColor)

            cvPurchase.setOnClickListener {
                listener?.onPurchaseClick(purchase)
            }

            cvPurchase.setOnLongClickListener {
                if (purchase.status == "pending") {
                    listener?.onMarkComplete(purchase)
                }
                true
            }
        }

        private fun formatDate(dateString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                date?.let { outputFormat.format(it) } ?: dateString
            } catch (e: Exception) {
                dateString
            }
        }
    }
}

