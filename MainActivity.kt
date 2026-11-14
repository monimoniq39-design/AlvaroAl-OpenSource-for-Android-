
package com.alvaroai.app

import android.os.Bundle
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.LinkedHashSet
import java.util.Random

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("AlvaroAI", MODE_PRIVATE)

        setContent {
            MaterialTheme {

                val scope = rememberCoroutineScope()

                val historial = remember {
                    mutableStateListOf<String>().apply {
                        val guardado = prefs.getStringSet(
                            "historial",
                            emptySet<String>()
                        )
                        addAll(guardado ?: emptySet())
                    }
                }

                var input by remember {
                    mutableStateOf("")
                }

                var respuesta by remember {
                    mutableStateOf(
                        "¡Hola! Soy ÁlvaroAI. ¿En qué puedo ayudarte?"
                    )
                }

                var bitmapActual by remember {
                    mutableStateOf<Bitmap?>(null)
                }

                var esperando by remember {
                    mutableStateOf(false)
                }

                var segundosRestantes by remember {
                    mutableIntStateOf(0)
                }

                var generando by remember {
                    mutableStateOf(false)
                }

                fun guardarHistorial() {
                    val copia = LinkedHashSet<String>()
                    copia.addAll(historial)

                    prefs.edit()
                        .putStringSet("historial", copia)
                        .apply()
                }

                fun agregarHistorial(texto: String) {
                    historial.add(0, texto)

                    while (historial.size > 50) {
                        historial.removeAt(historial.lastIndex)
                    }

                    guardarHistorial()
                }

                fun iniciarEspera() {
                    esperando = true
                    segundosRestantes = 45

                    scope.launch {
                        while (segundosRestantes > 0) {
                            delay(1000)
                            segundosRestantes--
                        }

                        esperando = false
                    }
                }

                suspend fun generarTexto(prompt: String): String {
                    return withContext(Dispatchers.IO) {

                        val encoded = URLEncoder.encode(
                            prompt,
                            "UTF-8"
                        )

                        val url = URL(
                            "https://text.pollinations.ai/$encoded"
                        )

                        val connection =
                            url.openConnection() as HttpURLConnection

                        try {
                            connection.requestMethod = "GET"
                            connection.connectTimeout = 20000
                            connection.readTimeout = 60000

                            val code = connection.responseCode

                            if (code !in 200..299) {
                                throw Exception("Error HTTP: $code")
                            }

                            connection.inputStream.bufferedReader()
                                .use { it.readText() }

                        } finally {
                            connection.disconnect()
                        }
                    }
                }

                suspend fun generarImagen(prompt: String): Bitmap {
                    return withContext(Dispatchers.IO) {

                        val encoded = URLEncoder.encode(
                            prompt,
                            "UTF-8"
                        )

                        val seed = Random().nextInt(999999)

                        val url = URL(
                            "https://image.pollinations.ai/prompt/$encoded" +
                            "?width=1024&height=1024&nologo=true&seed=$seed"
                        )

                        val connection =
                            url.openConnection() as HttpURLConnection

                        try {
                            connection.requestMethod = "GET"
                            connection.connectTimeout = 30000
                            connection.readTimeout = 120000

                            val code = connection.responseCode

                            if (code !in 200..299) {
                                throw Exception("Error HTTP: $code")
                            }

                            val bitmap = connection.inputStream.use {
                                BitmapFactory.decodeStream(it)
                            }

                            bitmap ?: throw Exception(
                                "No se pudo cargar la imagen."
                            )

                        } finally {
                            connection.disconnect()
                        }
                    }
                }

                fun enviarMensaje() {
                    val prompt = input.trim()

                    if (prompt.isEmpty()) {
                        Toast.makeText(
                            this@MainActivity,
                            "Escribe un mensaje primero",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }

                    if (esperando || generando) return

                    input = ""
                    generando = true
                    respuesta = "ÁlvaroAI está pensando..."

                    iniciarEspera()

                    scope.launch {
                        try {
                            val resultado = generarTexto(prompt)

                            respuesta = resultado

                            agregarHistorial(
                                "Tú: $prompt\nÁlvaroAI: $resultado"
                            )

                        } catch (e: Exception) {
                            respuesta =
                                "No se pudo obtener una respuesta: ${e.message}"
                        } finally {
                            generando = false
                        }
                    }
                }

                fun crearImagen() {
                    val prompt = input.trim()

                    if (prompt.isEmpty()) {
                        Toast.makeText(
                            this@MainActivity,
                            "Describe la imagen que quieres crear",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }

                    if (esperando || generando) return

                    input = ""
                    generando = true
                    bitmapActual = null
                    respuesta = "Generando imagen..."

                    iniciarEspera()

                    scope.launch {
                        try {
                            val imagen = generarImagen(prompt)

                            bitmapActual = imagen
                            respuesta = "¡Imagen generada correctamente!"

                            agregarHistorial(
                                "Imagen generada: $prompt"
                            )

                        } catch (e: Exception) {
                            respuesta =
                                "No se pudo generar la imagen: ${e.message}"
                        } finally {
                            generando = false
                        }
                    }
                }

                fun guardarImagen() {
                    val bitmap = bitmapActual

                    if (bitmap == null) {
                        Toast.makeText(
                            this@MainActivity,
                            "No hay ninguna imagen para guardar",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }

                    if (Build.VERSION.SDK_INT < 29) {
                        Toast.makeText(
                            this@MainActivity,
                            "El guardado automático requiere Android 10 o superior",
                            Toast.LENGTH_LONG
                        ).show()
                        return
                    }

                    try {
                        val nombre =
                            "AlvaroAI_${System.currentTimeMillis()}.png"

                        val values = ContentValues().apply {
                            put(
                                MediaStore.Images.Media.DISPLAY_NAME,
                                nombre
                            )
                            put(
                                MediaStore.Images.Media.MIME_TYPE,
                                "image/png"
                            )
                            put(
                                MediaStore.Images.Media.RELATIVE_PATH,
                                "Pictures/AlvaroAI"
                            )
                            put(
                                MediaStore.Images.Media.IS_PENDING,
                                1
                            )
                        }

                        val uri = contentResolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            values
                        )

                        if (uri == null) {
                            throw Exception("No se pudo crear el archivo")
                        }

                        try {
                            val outputStream =
                                contentResolver.openOutputStream(uri)

                            if (outputStream == null) {
                                throw Exception("No se pudo abrir el archivo")
                            }

                            outputStream.use {
                                bitmap.compress(
                                    Bitmap.CompressFormat.PNG,
                                    100,
                                    it
                                )
                            }

                            val finalValues = ContentValues().apply {
                                put(
                                    MediaStore.Images.Media.IS_PENDING,
                                    0
                                )
                            }

                            contentResolver.update(
                                uri,
                                finalValues,
                                null,
                                null
                            )

                            Toast.makeText(
                                this@MainActivity,
                                "Imagen guardada en Pictures/AlvaroAI",
                                Toast.LENGTH_LONG
                            ).show()

                        } catch (e: Exception) {
                            contentResolver.delete(uri, null, null)
                            throw e
                        }

                    } catch (e: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            "Error al guardar: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),

                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    Text(
                        text = "ÁlvaroAI",
                        style = MaterialTheme.typography.headlineLarge
                    )

                    Text(
                        text = "Tu inteligencia artificial",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    HorizontalDivider()

                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = {
                            Text("Escribe tu mensaje o describe una imagen")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5
                    )

                    Button(
                        onClick = { enviarMensaje() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !esperando && !generando
                    ) {
                        Text("Enviar mensaje")
                    }

                    OutlinedButton(
                        onClick = { crearImagen() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !esperando && !generando
                    ) {
                        Text("Generar imagen")
                    }

                    if (esperando) {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Tiempo de espera",
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Text(
                                    text = "Podrás enviar otra solicitud en $segundosRestantes segundos."
                                )

                                LinearProgressIndicator(
                                    progress = segundosRestantes / 45f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (generando) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Text("Procesando solicitud...")
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Respuesta de ÁlvaroAI",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(text = respuesta)
                        }
                    }

                    bitmapActual?.let { bitmap ->

                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(10.dp)
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Imagen generada por ÁlvaroAI",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 400.dp),
                                    contentScale = ContentScale.Fit
                                )

                                Button(
                                    onClick = { guardarImagen() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Guardar imagen en galería")
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Historial",
                            style = MaterialTheme.typography.titleLarge
                        )

                        TextButton(
                            onClick = {
                                historial.clear()
                                guardarHistorial()

                                Toast.makeText(
                                    this@MainActivity,
                                    "Historial borrado",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Text("Borrar")
                        }
                    }

                    if (historial.isEmpty()) {
                        Text("Todavía no hay conversaciones.")
                    }

                    historial.forEach { elemento ->

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    respuesta = elemento
                                }
                        ) {
                            Text(
                                text = elemento,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}
