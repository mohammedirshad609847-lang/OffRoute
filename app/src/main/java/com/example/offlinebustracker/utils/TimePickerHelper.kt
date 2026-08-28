package com.example.offlinebustracker.utils

import android.app.TimePickerDialog
import android.content.Context
import android.widget.EditText
import java.util.Locale

object TimePickerHelper {
    /**
     * Makes an EditText un-typeable and instead shows a 12-hour TimePickerDialog on click.
     * The EditText displays the 12-hour format, but stores the 24-hour "HH:mm" in its tag.
     */
    fun attachTimePicker(context: Context, editText: EditText, defaultTime24Hr: String) {
        editText.isFocusable = false
        editText.isClickable = true
        
        // Initialize state
        val safeTime = if (defaultTime24Hr.isEmpty()) "08:00" else defaultTime24Hr
        editText.tag = safeTime
        editText.setText(format24To12(safeTime))
        
        editText.setOnClickListener {
            val current24 = editText.tag as? String ?: "08:00"
            val parts = current24.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            
            TimePickerDialog(context, { _, hourOfDay, minute ->
                val new24 = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
                editText.tag = new24
                editText.setText(format24To12(new24))
            }, h, m, false).show() // false = 12-hour view
        }
    }
    
    fun format24To12(time24: String): String {
        val parts = time24.split(":")
        if (parts.size != 2) return time24
        var h = parts[0].toIntOrNull() ?: return time24
        val m = parts[1]
        val amPm = if (h >= 12) "PM" else "AM"
        if (h > 12) h -= 12
        if (h == 0) h = 12
        return String.format(Locale.US, "%02d:%s %s", h, m, amPm)
    }
}
