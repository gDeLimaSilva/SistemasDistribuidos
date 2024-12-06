package com.example.sistemasdistribuidos

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.sistemasdistribuidos.ui.theme.SistemasDistribuidosTheme
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import java.io.File
import android.Manifest

class MainActivity : ComponentActivity() {
    private lateinit var mqttClient: Mqtt3AsyncClient
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private var photoUri: Uri? = null

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupMqttClient()

        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                println("Foto capturada: $photoUri")
            } else {
                println("Falha ao capturar a foto.")
            }

        }

        enableEdgeToEdge()
        setContent {
            SistemasDistribuidosTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onPublishClick = { publishMessage() } ,
                        onCapturePhotoClick = {
                            checkCameraPermissionAndCapturePhoto()
                        }
                    )
                }
            }
        }
    }

    private fun setupMqttClient() {
        mqttClient = MqttClient.builder()
            .useMqttVersion3()
            .serverHost("broker.hivemq.com")
            .serverPort(1883)
            .buildAsync()

        mqttClient.connect().whenComplete { connAck, throwable ->
            if (throwable != null) {
                println("Erro na conexão: ${throwable.message}")
            } else {
                println("Conectado com sucesso!")
            }
        }
    }

    private fun publishMessage() {
        mqttClient.publishWith()
            .topic("test/topic")
            .payload("Olá MQTT!".toByteArray())
            .send()
            .whenComplete { pubAck, error ->
                if (error != null) {
                    println("Erro ao publicar: ${error.message}")
                } else {
                    println("Mensagem publicada com sucesso!")
                }
            }
    }

    private fun createImageFile(): Uri? {
        val imageFile = File.createTempFile(
            "photo_", ".jpg",
            this.externalCacheDir
        )
        return FileProvider.getUriForFile(
            this,
            "${this.packageName}.provider",
            imageFile
        )
    }

    private fun checkCameraPermissionAndCapturePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
            )
        {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
        else {
            capturePhoto()
        }
    }

    private fun capturePhoto() {
        val uri = createImageFile()
        if (uri != null) {
            photoUri = uri
            takePictureLauncher.launch(photoUri)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permissão concedida, tirar a foto
                capturePhoto()
            } else {
                // Permissão negada
                Toast.makeText(this, "Permissão para câmera negada", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onPublishClick: () -> Unit,
    onCapturePhotoClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(text = "MQTT Publish Example", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onPublishClick) {
            Text("Publicar Mensagem")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onCapturePhotoClick) {
            Text("Capturar Foto")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    SistemasDistribuidosTheme {
        MainScreen(onPublishClick = { }, onCapturePhotoClick = { })
    }
}