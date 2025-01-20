package com.longynus.churrasco

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_churrasco)

        // Inicializando os componentes
        val btnDatePicker: Button = findViewById(R.id.btnDatePicker)
        val btnTimePicker: Button = findViewById(R.id.btnTimePicker)
        val edtLocal: EditText = findViewById(R.id.edtLocal)
        val btnProximo: Button = findViewById(R.id.btnProximo)

        // Mapear os checkboxes da tela usando a lista centralizada
        ItemConstants.listaCompletaDeItens.map<ItemConstants.Item, Pair<CheckBox, String>> { item ->
            Pair(findViewById(item.checkboxId), item.label)
        }
        val checkboxContainer: LinearLayout = findViewById(R.id.checkboxContainer)

        // Iterar pela lista completa de itens
        ItemConstants.listaCompletaDeItens.forEach { item ->
            // Criar um CheckBox para cada item
            CheckBox(this).apply {
                text = item.label // Define o texto do checkbox com o label do item
                id = item.checkboxId // Define um ID único com base no ID do ItemConstants
                textSize = 16f
            }

        }
        // Configuração do DatePicker
        btnDatePicker.setOnClickListener {
            showDatePicker(btnDatePicker)
        }

        // Configuração do TimePicker
        btnTimePicker.setOnClickListener {
            showTimePicker(btnTimePicker)
        }

        btnProximo.setOnClickListener {
            val local = edtLocal.text.toString()
            val fornecidos = mutableListOf<String>()
            val itensNaoFornecidos = ItemConstants.listaCompletaDeItens
                .filter { item -> item.label !in fornecidos }
                .map { it.label } // Extraindo apenas o nome do item para enviar como String

            // Iterar pelos filhos do container para verificar quais CheckBox foram marcados
            for (i in 0 until checkboxContainer.childCount) {
                val view = checkboxContainer.getChildAt(i)
                if (view is CheckBox && view.isChecked) {
                    fornecidos.add(view.text.toString()) // Adicionar o texto dos CheckBox marcados
                }
            }

            // Adicionar os dados coletados como extras na Intent
            if (selectedDate.isEmpty() || selectedTime.isEmpty() || local.isEmpty()) {
                Toast.makeText(this, "Por favor, preencha todos os campos.", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, CreateChurrascoActivity2::class.java)
                intent.putExtra("churrascoDate", selectedDate)
                intent.putExtra("hora", selectedTime)
                intent.putExtra("local", local)
                intent.putStringArrayListExtra("fornecidos", ArrayList(fornecidos))
                intent.putStringArrayListExtra("itensNaoFornecidos", ArrayList(itensNaoFornecidos))

                startActivity(intent)
            }

        }
    }

    private fun showDatePicker(button: Button) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, y, m, d ->
            selectedDate = "$d/${m + 1}/$y"
            button.text = selectedDate
        }, year, month, day).show()
    }

    @SuppressLint("DefaultLocale")
    private fun showTimePicker(button: Button) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, h, m ->
            selectedTime = String.format("%02d:%02d", h, m)
            button.text = selectedTime
        }, hour, minute, true).show()
    }
}







