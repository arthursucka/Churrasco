package com.longynus.churrasco

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class CreateChurrascoActivity1 : AppCompatActivity() {

    private var selectedDate: String = ""
    private var selectedTime: String = ""
    private var selectedYear: Int? = null
    private var selectedMonth: Int? = null
    private var selectedDay: Int? = null
    private var selectedHour: Int? = null
    private var selectedMinute: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_churrasco)
        TopBarHelper.setup(this, getString(R.string.create_title))
        KeyboardInsetsHelper.setup(this)

        val btnDatePicker: Button = findViewById(R.id.btnDatePicker)
        val btnTimePicker: Button = findViewById(R.id.btnTimePicker)
        val edtLocal: EditText = findViewById(R.id.edtLocal)
        val checkboxContainer: LinearLayout = findViewById(R.id.checkboxContainer)
        val btnProximo: Button = findViewById(R.id.btnProximo)

        checkboxContainer.removeAllViews()
        ItemConstants.listaCompletaDeItens.forEach { item ->
            val cb = CheckBox(this).apply {
                text = item
                textSize = 16f
                setTextColor(getColor(R.color.mainInk))
                buttonTintList = ColorStateList.valueOf(getColor(R.color.mainPrimary))
                background = getDrawable(R.drawable.bg_create_chip)
                setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8.dp()
                }
            }
            checkboxContainer.addView(cb)
        }

        btnDatePicker.setOnClickListener {
            showDatePicker(btnDatePicker)
        }

        btnTimePicker.setOnClickListener {
            showTimePicker(btnTimePicker)
        }

        btnProximo.setOnClickListener {
            val local = edtLocal.text.toString().trim()
            val fornecidos = mutableListOf<String>()

            for (i in 0 until checkboxContainer.childCount) {
                val view = checkboxContainer.getChildAt(i)
                if (view is CheckBox && view.isChecked) {
                    fornecidos.add(view.text.toString())
                }
            }

            if (selectedDate.isEmpty()) {
                Toast.makeText(this, "Escolha uma data para o churrasco.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedTime.isEmpty()) {
                Toast.makeText(this, "Escolha um horário para o churrasco.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isSelectedDateTimeInFuture()) {
                Toast.makeText(this, "Escolha uma data e horário futuros.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (local.length < MIN_LOCAL_LENGTH) {
                Toast.makeText(
                    this,
                    "Informe um local com pelo menos $MIN_LOCAL_LENGTH caracteres.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (fornecidos.isEmpty()) {
                Toast.makeText(this, "Marque pelo menos um item que você vai garantir.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val itensNaoFornecidos = ItemConstants.listaCompletaDeItens
                .filter { it !in fornecidos }

            val intent = Intent(this, CreateChurrascoActivity2::class.java).apply {
                putExtra("churrascoDate", selectedDate)
                putExtra("hora", selectedTime)
                putExtra("local", local)
                putStringArrayListExtra("fornecidos", ArrayList(fornecidos))
                putStringArrayListExtra("itensNaoFornecidos", ArrayList(itensNaoFornecidos))
            }
            startActivity(intent)
        }
    }

    @SuppressLint("DefaultLocale")
    private fun showDatePicker(button: Button) {
        val current = Calendar.getInstance()
        val dialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedYear = year
                selectedMonth = month
                selectedDay = day
                selectedDate = ChurrascoDateUtils.formatDate(day, month, year)
                button.text = selectedDate
            },
            current.get(Calendar.YEAR),
            current.get(Calendar.MONTH),
            current.get(Calendar.DAY_OF_MONTH)
        )
        dialog.datePicker.minDate = ChurrascoDateUtils.startOfToday().timeInMillis
        dialog.show()
    }

    @SuppressLint("DefaultLocale")
    private fun showTimePicker(button: Button) {
        val current = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hour, minute ->
                selectedHour = hour
                selectedMinute = minute
                selectedTime = ChurrascoDateUtils.formatTime(hour, minute)
                button.text = selectedTime
            },
            current.get(Calendar.HOUR_OF_DAY),
            current.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun isSelectedDateTimeInFuture(): Boolean =
        ChurrascoDateUtils.selectedDateTimeIsFuture(
            selectedYear,
            selectedMonth,
            selectedDay,
            selectedHour,
            selectedMinute
        )

    companion object {
        private const val MIN_LOCAL_LENGTH = 3
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
