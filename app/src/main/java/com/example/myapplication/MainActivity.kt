package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity(), SensorEventListener {

    // Gerenciador e sensor de batimentos cardíacos
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null

    //Armazena o timestamp da última vez em que os dados foram enviados.
    private var lastSentTime: Long = 0

    // Variável observável para armazenar a última leitura válida de BPM
    private var _heartRate by mutableStateOf(0f)
    val heartRate: Float get() = _heartRate

    // Lançador de permissão para sensores corporais
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) registerSensorListener()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicialização dos sensores
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        // Verifica permissão e registra listener
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.BODY_SENSORS)
        } else {
            registerSensorListener()
        }

        // UI principal
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        HeartRateDisplay(heartRate)
                    }
                }
            }
        }
    }

    /**
     * Envia os dados de batimento cardíaco ao Firebase Realtime Database.
     */
    private fun sendHeartRateToRealtimeDB(bpm: Float) {
        val currentTime = System.currentTimeMillis()

        // Verifica se passou pelo menos 10 segundos (30.000 milissegundos)
        if (currentTime - lastSentTime < 10_000) return

        lastSentTime = currentTime

        val database = FirebaseDatabase.getInstance()
        val ref = database.getReference("heartrate")

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())
            .format(Date())

        val data = mapOf(
            "bpm" to bpm,
            "timestamp" to timestamp
        )

        ref.child(timestamp).setValue(data)
            .addOnSuccessListener {
                Log.d("RealtimeDB", "Dados enviados com sucesso")
            }
            .addOnFailureListener { e ->
                Log.e("RealtimeDB", "Erro ao enviar dados", e)
            }
    }

    /**
     * Registra o listener do sensor de batimentos cardíacos.
     */
    private fun registerSensorListener() {
        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    /**
     * Manipula os dados recebidos do sensor.
     */
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
            val bpm = event.values.getOrNull(0) ?: 0f

            _heartRate = bpm

            if(_heartRate > 0 ){
                Log.d("Heart", "batimentos coletados");
                sendHeartRateToRealtimeDB(bpm)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    /**
     * Composable que exibe os batimentos cardíacos e alerta caso o pulso esteja afastado do sensor.
     */
    @Composable
    fun HeartRateDisplay(heartRate: Float) {
        var showWarning by remember { mutableStateOf(false) }
        var lastUpdate by remember { mutableStateOf(System.currentTimeMillis()) }

        // Atualiza o tempo da última leitura válida
        LaunchedEffect(heartRate) {
            if (heartRate > 0f) {
                lastUpdate = System.currentTimeMillis()
            }
        }

        // Verifica continuamente se houve perda de contato
        LaunchedEffect(Unit) {
            while (true) {
                val now = System.currentTimeMillis()
                showWarning = now - lastUpdate > 3000 // 3 segundos sem leitura válida
                kotlinx.coroutines.delay(1000)
            }
        }

        // Exibe alerta ou valor de BPM
        if (showWarning) {
            Text(text = "Aproxime o pulso do sensor")
        } else {
            Text(
                text = "${heartRate.toInt()} BPM",
                fontSize = 32.sp, // aumenta o tamanho da fonte
                fontWeight = FontWeight.Bold // aplica o negrito
            )

        }
    }
}
