package com.example.sistemasdistribuidos

import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.sistemasdistribuidos.ui.theme.SistemasDistribuidosTheme
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.time.LocalDate
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import java.io.ByteArrayOutputStream
import org.json.JSONObject
import coil.compose.rememberImagePainter

class MainActivity : ComponentActivity() {
    private lateinit var mqttClient: Mqtt3AsyncClient
    private var currentTopic: String? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var getImageLauncher: ActivityResultLauncher<String>

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar o cliente MQTT
        setupMqttClient()

        // Provedor de localização
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        getImageLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                uri?.let {
                    val imagePath = getRealPathFromURI(it)

                    // Obtenha a localização antes de chamar a função
                    fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            // Chama a função com a localização e o caminho da imagem
                            publishMessage(location, imagePath)
                        } else {
                            Toast.makeText(this, "Não foi possível obter a localização", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }


        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    fetchLocationAndPublishMessage() // Tenta novamente após a permissão
                } else {
                    Toast.makeText(this, "Permissão de localização negada", Toast.LENGTH_SHORT)
                        .show()
                }
            }

        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    fetchLocationAndPublishMessage() // Tenta novamente após a permissão
                } else {
                    Toast.makeText(this, "Permissão de localização negada", Toast.LENGTH_SHORT).show()
                }
            }

        // Verifica se a permissão de localização já foi concedida
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Solicita permissão ao usuário
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            // Caso já tenha a permissão, inicia a lógica de localização
            fetchLocationAndPublishMessage()
        }

        setContent {
            SistemasDistribuidosTheme {
                MainScreen(
                    onConnectClick = { topic -> connectToTopic(topic) },
                    onDisconnectClick = { disconnectFromTopic() }
                )
            }
        }
    }

    fun encodeImageToBase64(imagePath: String): String {
        val bitmap = BitmapFactory.decodeFile(imagePath)
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun getRealPathFromURI(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.moveToFirst()
        val idx = cursor?.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
        val filePath = cursor?.getString(idx ?: -1)
        cursor?.close()
        return filePath ?: ""
    }


    private fun setupMqttClient() {
        mqttClient = MqttClient.builder()
            .useMqttVersion3()
            .serverHost("broker.hivemq.com")
            .serverPort(1883)
            .buildAsync()

        mqttClient.connect().whenComplete { connAck, throwable ->
            runOnUiThread {
                if (throwable != null) {
                    println("Erro na conexão: ${throwable.message}")
                    Toast.makeText(this, "Erro ao conectar ao broker", Toast.LENGTH_SHORT).show()
                } else {
                    println("Conectado com sucesso!")
                    Toast.makeText(this, "Conectado ao broker MQTT", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun connectToTopic(topic: String) {
        currentTopic = topic

        mqttClient.subscribeWith()
            .topicFilter(topic)
            .send()
            .whenComplete { subAck, error ->
                runOnUiThread {
                    if (error != null) {
                        println("Erro ao se inscrever no tópico: ${error.message}")
                        Toast.makeText(this, "Erro ao se inscrever no tópico", Toast.LENGTH_SHORT).show()
                    } else {
                        println("Inscrito no tópico: $topic")
                        Toast.makeText(this, "Conectado ao tópico $topic", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun disconnectFromTopic() {
        val topic = currentTopic
        if (topic != null) {
            mqttClient.unsubscribeWith()
                .topicFilter(topic)
                .send()
                .whenComplete { unsubAck, error ->
                    runOnUiThread {
                        if (error != null) {
                            println("Erro ao se desinscrever do tópico: ${error.message}")
                            Toast.makeText(
                                this,
                                "Erro ao se desinscrever do tópico",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            println("Desinscrito do tópico: $topic")
                            Toast.makeText(
                                this,
                                "Desconectado do tópico $topic",
                                Toast.LENGTH_SHORT
                            ).show()
                            currentTopic = null
                        }
                    }
                }
        } else {
            Toast.makeText(this, "Nenhum tópico conectado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchLocationAndPublishMessage() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Solicitar permissão usando o launcher previamente registrado
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                // Aqui você pode agora passar o caminho da imagem
                publishMessage(location, imagePath = "CAMINHO_DA_IMAGEM_AQUI")
            } else {
                Toast.makeText(this, "Não foi possível obter a localização", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }


    @SuppressLint("NewApi")
    private fun publishMessage(location: android.location.Location, imagePath: String) {
        val topic = currentTopic
        if (topic != null) {
            val latitude = location.latitude
            val longitude = location.longitude
            val currentDate = LocalDate.now().toString()
            val imageBase64 = encodeImageToBase64(imagePath)

            val jsonPayload = """
            {
                "localizacao": {
                    "latitude": $latitude,
                    "longitude": $longitude
                },
                "data": "$currentDate",
                "imagem": "$imageBase64"
            }
        """.trimIndent()

            mqttClient.publishWith()
                .topic(topic)
                .payload(jsonPayload.toByteArray())
                .send()
                .whenComplete { pubAck, error ->
                    runOnUiThread {
                        if (error != null) {
                            println("Erro ao publicar: ${error.message}")
                            showDialog("Erro ao publicar mensagem", error.message ?: "Erro desconhecido")
                        } else {
                            println("Mensagem publicada com sucesso!")
                            Toast.makeText(this, "Mensagem publicada com sucesso!", Toast.LENGTH_SHORT).show()

                            mqttClient.publishes(MqttGlobalPublishFilter.ALL) { publish ->
                                val payload = String(publish.payloadAsBytes, Charsets.UTF_8)
                                runOnUiThread {
                                    val jsonObject = JSONObject(payload)
                                    val latitude = jsonObject.getJSONObject("localizacao").getDouble("latitude")
                                    val longitude = jsonObject.getJSONObject("localizacao").getDouble("longitude")
                                    val data = jsonObject.getString("data")
                                    val imageBase64 = jsonObject.getString("imagem")

                                    // Decodificar a imagem
                                    val decodedBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

                                    // Exibir a imagem e outros dados
                                    showReceivedMessageDialog(latitude, longitude, data, bitmap)
                                }
                            }
                        }
                    }
                }
        }
    }

    fun showReceivedMessageDialog(latitude: Double, longitude: Double, data: String, image: Bitmap) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Mensagem Recebida")
        builder.setMessage("Data: $data\nLatitude: $latitude\nLongitude: $longitude")
        val imageView = ImageView(this)
        imageView.setImageBitmap(image)
        builder.setView(imageView)
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        builder.create().show()
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    @Composable
    fun MainScreen(
        onConnectClick: (String) -> Unit,
        onDisconnectClick: () -> Unit
    ) {
        var topic by rememberSaveable { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "IC820 - Sistemas Distribuídos", style = MaterialTheme.typography.headlineSmall)

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("Tópico") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { onConnectClick(topic) }) {
                Text("Conectar")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = onDisconnectClick) {
                Text("Desconectar")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = { getImageLauncher.launch("image/*") }) {
                Text("Enviar Mensagem")
            }
        }
    }

}