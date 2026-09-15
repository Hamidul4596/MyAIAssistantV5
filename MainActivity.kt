package com.myaiaassistant.v4

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.myaiaassistant.v4.network.ApiClient
import com.myaiaassistant.v4.security.KillSwitch
import com.myaiaassistant.v4.security.SecurityGate
import com.myaiaassistant.v4.tools.AppLauncher
import com.myaiaassistant.v4.voice.TtsManager
import kotlinx.coroutines.launch

data class ChatLine(val who: String, val text: String)

class MainActivity : ComponentActivity() {

    private val history = mutableStateListOf<ChatLine>()
    private var pendingConfirmation by mutableStateOf<String?>(null)
    private var isBusy by mutableStateOf(false)

    private lateinit var tts: TtsManager

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchVoiceRecognizer() }

    private val voiceRecognizer = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) handleUserInput(text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TtsManager(this)

        setContent {
            MaterialTheme {
                AssistantScreen(
                    history = history,
                    isBusy = isBusy,
                    pendingConfirmation = pendingConfirmation,
                    onListen = { requestMicAndListen() },
                    onSendText = { text -> handleUserInput(text) },
                    onConfirm = { confirmed -> resolveConfirmation(confirmed) },
                    onKillSwitch = { stop ->
                        if (stop) KillSwitch.stop() else KillSwitch.resume()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }

    private fun requestMicAndListen() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) launchVoiceRecognizer() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun launchVoiceRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_EXTRA_LANGUAGES, arrayOf("en-US"))
        }
        runCatching { voiceRecognizer.launch(intent) }
    }

    /** Main entry point for anything the user said or typed. */
    private fun handleUserInput(text: String) {
        history.add(ChatLine("You", text))

        if (!KillSwitch.canExecute()) {
            val msg = "Kill switch is on. Turn it off before I can act."
            history.add(ChatLine("Assistant", msg))
            tts.speak(msg)
            return
        }

        val sensitiveAction = detectSensitiveAction(text)
        if (sensitiveAction != null && SecurityGate.requiresConfirmation(sensitiveAction)) {
            pendingConfirmation = text
            return
        }

        // Local, no-AI-needed actions (open app / web search) short-circuit the network call.
        if (AppLauncher.tryHandleLocally(this, text)) {
            val msg = "Done."
            history.add(ChatLine("Assistant", msg))
            tts.speak(msg)
            return
        }

        sendToBackend(text)
    }

    private fun resolveConfirmation(confirmed: Boolean) {
        val text = pendingConfirmation
        pendingConfirmation = null
        if (text == null) return
        if (!confirmed) {
            val msg = "Cancelled."
            history.add(ChatLine("Assistant", msg))
            tts.speak(msg)
            return
        }
        if (AppLauncher.tryHandleLocally(this, text)) {
            val msg = "Done."
            history.add(ChatLine("Assistant", msg))
            tts.speak(msg)
        } else {
            sendToBackend(text)
        }
    }

    private fun sendToBackend(text: String) {
        isBusy = true
        lifecycleScope.launch {
            val pastTurns = history.takeLast(10).map { line ->
                (if (line.who == "You") "user" else "assistant") to line.text
            }
            when (val result = ApiClient.sendCommand(BuildConfig.BACKEND_BASE_URL, text, pastTurns)) {
                is ApiClient.Result.Success -> {
                    history.add(ChatLine("Assistant", result.reply))
                    tts.speak(result.reply)
                }
                is ApiClient.Result.Failure -> {
                    val msg = "সমস্যা হয়েছে: ${result.message}"
                    history.add(ChatLine("Assistant", msg))
                }
            }
            isBusy = false
        }
    }

    /** Very small keyword matcher against SecurityGate's blocked-action list. */
    private fun detectSensitiveAction(text: String): String? {
        val lower = text.lowercase()
        return when {
            "delete" in lower || "মুছে" in lower -> "delete_files"
            "password" in lower || "পাসওয়ার্ড" in lower -> "change_password"
            "send money" in lower || "টাকা পাঠা" in lower -> "send_money"
            "factory reset" in lower -> "factory_reset"
            else -> null
        }
    }
}

@Composable
fun AssistantScreen(
    history: List<ChatLine>,
    isBusy: Boolean,
    pendingConfirmation: String?,
    onListen: () -> Unit,
    onSendText: (String) -> Unit,
    onConfirm: (Boolean) -> Unit,
    onKillSwitch: (Boolean) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var killSwitchOn by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("My AI Assistant V4", style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Kill switch", style = MaterialTheme.typography.labelSmall)
                Switch(checked = killSwitchOn, onCheckedChange = {
                    killSwitchOn = it
                    onKillSwitch(it)
                })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("বাংলা বা English-এ বলুন বা লিখুন")
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Command") }
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onListen, enabled = !isBusy) { Text("🎙 Voice") }
            Button(
                onClick = { if (text.isNotBlank()) { onSendText(text); text = "" } },
                enabled = !isBusy
            ) { Text("Send") }
            if (isBusy) CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }

        if (pendingConfirmation != null) {
            Spacer(Modifier.height(12.dp))
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("এই কাজটি sensitive: \"$pendingConfirmation\"")
                    Text("চালিয়ে যাবে?")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onConfirm(true) }) { Text("হ্যাঁ") }
                        OutlinedButton(onClick = { onConfirm(false) }) { Text("না") }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        LazyColumn {
            items(history.takeLast(50)) { line ->
                Text("${line.who}: ${line.text}", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}
