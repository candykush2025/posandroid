package com.blackcode.poscandykush

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class ExpenseAdapter(
    private val expenses: MutableList<Expense>
) : RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    private var listener: OnExpenseClickListener? = null

    interface OnExpenseClickListener {
        fun onExpenseClick(expense: Expense)
        fun onExpenseDelete(expense: Expense)
    }

    fun setOnExpenseClickListener(listener: OnExpenseClickListener) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val expense = expenses[position]
        holder.bind(expense)
    }

    override fun getItemCount(): Int = expenses.size

    fun updateExpenses(newExpenses: List<Expense>) {
        expenses.clear()
        expenses.addAll(newExpenses)
        notifyDataSetChanged()
    }

    inner class ExpenseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cvExpense: CardView = itemView.findViewById(R.id.cv_expense)
        private val tvDescription: TextView = itemView.findViewById(R.id.tv_description)
        private val tvDateTime: TextView = itemView.findViewById(R.id.tv_date_time)
        private val tvAmount: TextView = itemView.findViewById(R.id.tv_amount)

        fun bind(expense: Expense) {
            tvDescription.text = expense.description ?: "No description"
            tvDateTime.text = "${formatDate(expense.date ?: "")} ${expense.time ?: ""}"
            tvAmount.text = expense.getFormattedAmount()

            cvExpense.setOnClickListener {
                listener?.onExpenseClick(expense)
            }

            cvExpense.setOnLongClickListener {
                listener?.onExpenseDelete(expense)
                true
            }
        }

        private fun formatDate(dateString: String): String {
            return try {
                if (dateString.isEmpty()) return "No date"
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
