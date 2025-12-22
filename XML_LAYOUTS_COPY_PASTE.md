# XML Layout Files - Copy-Paste Ready

This file contains all the XML layouts ready to copy-paste into Android Studio. Create each file manually in Android Studio to avoid encoding issues.

---

## How to Create Each File

1. In Android Studio, right-click on `app/src/main/res/layout`
2. Select **New → Layout Resource File**
3. Enter the file name (without .xml)
4. Click OK
5. Replace the generated content with the code below
6. Save (Ctrl+S)

---

## File 1: item_purchase.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/cv_purchase"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="8dp"
    app:cardCornerRadius="8dp"
    app:cardElevation="4dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/tv_supplier_name"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Supplier Name"
                android:textSize="18sp"
                android:textStyle="bold"
                android:textColor="@color/black" />

            <TextView
                android:id="@+id/tv_status"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Pending"
                android:textSize="14sp"
                android:textStyle="bold"
                android:textColor="@color/status_pending" />

        </LinearLayout>

        <TextView
            android:id="@+id/tv_date"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Date: Dec 20, 2025"
            android:textSize="14sp"
            android:textColor="@color/text_secondary"
            android:layout_marginTop="4dp" />

        <TextView
            android:id="@+id/tv_due_date"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Due: Dec 27, 2025"
            android:textSize="14sp"
            android:textColor="@color/text_secondary"
            android:layout_marginTop="4dp" />

        <TextView
            android:id="@+id/tv_total"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="$0.00"
            android:textSize="16sp"
            android:textStyle="bold"
            android:textColor="@color/primary_green"
            android:layout_marginTop="8dp" />

    </LinearLayout>

</androidx.cardview.widget.CardView>
```

---

## File 2: activity_purchasing.xml

Copy the content from `activity_customer_invoice.xml` and make these changes:
- Change title from "Customer Invoices" to "Purchasing"
- Change `android:id="@+id/rv_invoices"` to `android:id="@+id/rv_purchases"`
- Change `android:id="@+id/fab_add_invoice"` to `android:id="@+id/fab_add_purchase"`
- Change empty state text from "No invoices found" to "No purchases found"

---

## File 3: activity_expense.xml

Copy the content from `activity_customer_invoice.xml` and make these changes:
- Change title from "Customer Invoices" to "Expenses"
- Change `android:id="@+id/rv_invoices"` to `android:id="@+id/rv_expenses"`
- Change `android:id="@+id/fab_add_invoice"` to `android:id="@+id/fab_add_expense"`
- Change empty state text from "No invoices found" to "No expenses found"
- Add after the empty state TextView:

```xml
<TextView
    android:id="@+id/tv_total"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="top|end"
    android:layout_margin="16dp"
    android:text="Total: $0.00"
    android:textSize="18sp"
    android:textStyle="bold"
    android:background="@color/white"
    android:padding="12dp"
    android:elevation="4dp"
    android:textColor="@color/primary_green"
    android:visibility="gone" />
```

---

## File 4: activity_add_purchase.xml

Copy the content from `activity_add_invoice.xml` and make these changes:

1. Change title from "Create Invoice" to "Create Purchase"
2. Change "Customer Information" to "Supplier Information"
3. Change `et_customer_name` to `et_supplier_name` and hint to "Supplier Name"
4. Change `et_invoice_date` to `et_purchase_date` and label to "Purchase Date"
5. Update due date label to "Due Date (Monday-Friday only)"
6. After the due date section, add this reminder section:

```xml
<!-- Reminder Section -->
<TextView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="Reminder (Optional)"
    android:textSize="18sp"
    android:textStyle="bold"
    android:textColor="@color/black"
    android:layout_marginBottom="8dp" />

<RadioGroup
    android:id="@+id/rg_reminder_type"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="8dp">

    <RadioButton
        android:id="@+id/rb_no_reminder"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="No Reminder"
        android:checked="true" />

    <RadioButton
        android:id="@+id/rb_days_before"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Days Before" />

    <RadioButton
        android:id="@+id/rb_specific_date"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Specific Date and Time" />

</RadioGroup>

<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/til_days_before"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="8dp"
    android:hint="Days Before Due Date"
    android:visibility="gone">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/et_days_before"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="number" />

</com.google.android.material.textfield.TextInputLayout>

<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/til_reminder_date"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="8dp"
    android:hint="Reminder Date"
    android:visibility="gone">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/et_reminder_date"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="date"
        android:focusable="false"
        android:clickable="true" />

</com.google.android.material.textfield.TextInputLayout>

<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/til_reminder_time"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="16dp"
    android:hint="Reminder Time"
    android:visibility="gone">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/et_reminder_time"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="time"
        android:focusable="false"
        android:clickable="true" />

</com.google.android.material.textfield.TextInputLayout>
```

7. Change `rv_invoice_items` to `rv_purchase_items`
8. Change `btn_save_invoice` to `btn_save_purchase` and text to "Save Purchase"

---

## File 5: activity_add_expense.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/white">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <View
            android:id="@+id/status_bar_background"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:background="@color/primary_green" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="@color/primary_green"
            android:orientation="vertical">

            <androidx.constraintlayout.widget.ConstraintLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:padding="16dp">

                <ImageButton
                    android:id="@+id/btn_back"
                    android:layout_width="40dp"
                    android:layout_height="40dp"
                    android:src="@drawable/ic_arrow_left"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="Back"
                    app:tint="@color/white"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintTop_toTopOf="parent"
                    app:layout_constraintBottom_toBottomOf="parent" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Add Expense"
                    android:textSize="20sp"
                    android:textStyle="bold"
                    android:textColor="@color/white"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintTop_toTopOf="parent"
                    app:layout_constraintBottom_toBottomOf="parent" />

            </androidx.constraintlayout.widget.ConstraintLayout>

        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Expense Details"
                android:textSize="18sp"
                android:textStyle="bold"
                android:textColor="@color/black"
                android:layout_marginBottom="8dp" />

            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:hint="Description">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/et_description"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="textMultiLine"
                    android:minLines="2"
                    android:maxLines="4" />

            </com.google.android.material.textfield.TextInputLayout>

            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:hint="Amount"
                app:prefixText="$">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/et_amount"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="numberDecimal" />

            </com.google.android.material.textfield.TextInputLayout>

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Date and Time"
                android:textSize="18sp"
                android:textStyle="bold"
                android:textColor="@color/black"
                android:layout_marginBottom="8dp" />

            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:hint="Select Date">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/et_date"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="date"
                    android:focusable="false"
                    android:clickable="true" />

            </com.google.android.material.textfield.TextInputLayout>

            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="32dp"
                android:hint="Select Time">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/et_time"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="time"
                    android:focusable="false"
                    android:clickable="true" />

            </com.google.android.material.textfield.TextInputLayout>

            <Button
                android:id="@+id/btn_save_expense"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Save Expense"
                android:backgroundTint="@color/primary_green"
                android:textColor="@color/white"
                android:layout_marginBottom="32dp" />

            <ProgressBar
                android:id="@+id/progress_bar"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="center"
                android:visibility="gone"
                android:indeterminateTint="@color/primary_green" />

        </LinearLayout>

    </LinearLayout>

</ScrollView>
```

---

## Quick Creation Steps

1. Create `item_purchase.xml` - Copy full code above
2. Create `activity_purchasing.xml` - Copy from `activity_customer_invoice.xml` and modify IDs
3. Create `activity_expense.xml` - Copy from `activity_customer_invoice.xml` and modify IDs  
4. Create `activity_add_purchase.xml` - Copy from `activity_add_invoice.xml` and add reminder section
5. Create `activity_add_expense.xml` - Copy full code above

**Total Time**: 5-10 minutes

After creating all files, run:
```bash
./gradlew clean assembleDebug
```

Everything should build successfully!

