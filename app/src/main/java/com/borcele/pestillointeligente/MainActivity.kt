package com.borcele.pestillointeligente

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var mqttClient: MqttAndroidClient
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var txtStatus: TextView

    private val SERVER_URI = "tcp://broker.hivemq.com:1883"
    private val TOPIC_CONTROL = "casa/bollua_992/pestillo/control"
    private val TOPIC_STATUS = "casa/bollua_992/pestillo/estado"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        txtStatus = findViewById(R.id.txtStatus)
        setupMqtt()

        findViewById<Button>(R.id.btnOpen).setOnClickListener { sendCommand("OPEN") }
        findViewById<Button>(R.id.btnClose).setOnClickListener { sendCommand("CLOSE") }
        findViewById<Button>(R.id.btnHistory).setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun setupMqtt() {
        val clientId = MqttClient.generateClientId()
        mqttClient = MqttAndroidClient(this.applicationContext, SERVER_URI, clientId)

        // Configuración robusta de conexión
        val mqttOptions = MqttConnectOptions().apply {
            isCleanSession = true      // Evita procesar mensajes viejos acumulados
            connectionTimeout = 10     // Timeout rápido (10s) para no congelar la UI esperando
            keepAliveInterval = 20     // Ping cada 20s para mantener la conexión viva
        }

        try {
            // Pasamos las opciones y el callback directamente en el connect
            mqttClient.connect(mqttOptions, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    // Éxito: Feedback visual inmediato y suscripción
                    txtStatus.text = "Conectado MQTT"
                    txtStatus.setTextColor(Color.GREEN)
                    subscribeToTopic()
                }

                override fun onFailure(asyncActionToken: IMqttToken, ex: Throwable) {
                    // Fallo: Feedback visual y log del error real
                    txtStatus.text = "Error Conexión: ${ex.message}"
                    txtStatus.setTextColor(Color.RED)
                    ex.printStackTrace()
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
            txtStatus.text = "Excepción Inicialización"
            txtStatus.setTextColor(Color.RED)
        }
    }

    private fun subscribeToTopic() {
        try {
            mqttClient.subscribe(TOPIC_STATUS, 0)
            mqttClient.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {}
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    runOnUiThread { txtStatus.text = "Estado: ${message.toString()}" }
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })
        } catch (e: MqttException) { e.printStackTrace() }
    }

    private fun sendCommand(cmd: String) {
        if (mqttClient.isConnected) {
            mqttClient.publish(TOPIC_CONTROL, MqttMessage(cmd.toByteArray()))
            logActivity(cmd)
        }
    }

    private fun logActivity(action: String) {
        val user = auth.currentUser ?: return
        val log = hashMapOf("usuario" to (user.email ?: "Anon"), "accion" to action, "fecha" to Date())
        db.collection("historial_pestillo").add(log)
    }
}