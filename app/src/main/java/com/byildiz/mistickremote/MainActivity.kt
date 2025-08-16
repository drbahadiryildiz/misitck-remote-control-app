
package com.byildiz.mistickremote

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import com.github.kunal52.androidtvremote.AndroidRemoteTv
import com.github.kunal52.androidtvremote.model.Remotemessage

data class TvDevice(val name: String, val host: InetAddress, val port: Int)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                RemoteApp()
            }
        }
    }
}

@Composable
fun RemoteApp() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var devices by remember { mutableStateOf(listOf<TvDevice>()) }
    var isDiscovering by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<TvDevice?>(null) }
    var connected by remember { mutableStateOf(false) }
    var pinNeeded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pinCode by remember { mutableStateOf("") }

    // Android TV Remote client (library)
    val remote = remember { AndroidRemoteTv() }

    fun connectTo(device: TvDevice) {
        error = null
        selected = device
        scope.launch(Dispatchers.IO) {
            remote.connect(device.host.hostAddress, object : AndroidRemoteTv.AndroidTvListener {
                override fun onSessionCreated() {
                    // do nothing
                }
                override fun onSecretRequested() {
                    // ask for 6-digit PIN from user
                    pinNeeded = true
                }
                override fun onPaired() {
                    // connected to pairing server, now connecting to remote port...
                }
                override fun onConnectingToRemote() { }
                override fun onConnected() {
                    connected = true
                }
                override fun onDisconnect() {
                    connected = false
                }
                override fun onError(errorMsg: String) {
                    error = errorMsg
                    connected = false
                }
            })
        }
    }

    fun sendKey(key: Remotemessage.RemoteKeyCode, longPress: Boolean = false) {
        if (!connected) return
        val dir = if (longPress) Remotemessage.RemoteDirection.LONG else Remotemessage.RemoteDirection.SHORT
        scope.launch(Dispatchers.IO) {
            remote.sendCommand(key, dir)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Mi Stick Remote", fontWeight = FontWeight.SemiBold) })
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !isDiscovering,
                    onClick = {
                        isDiscovering = true
                        discoverDevices(
                            context = context,
                            onFound = { d ->
                                devices = (devices + d).distinctBy { it.host.hostAddress }
                            },
                            onDone = { isDiscovering = false }
                        )
                    }) {
                    Text("Cihazları Tara (mDNS)")
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { devices = emptyList() }) {
                    Text("Listeyi Temizle")
                }
            }

            Spacer(Modifier.height(8.dp))
            if (devices.isEmpty()) {
                Text("Aynı Wi‑Fi'daki Android TV / Mi Stick cihazları burada listelenecek.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(devices) { d ->
                        Card(onClick = { connectTo(d) }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text(d.name, style = MaterialTheme.typography.titleMedium)
                                Text("${d.host.hostAddress}:${d.port}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            Divider(Modifier.padding(vertical = 8.dp))

            if (connected) {
                Text("Bağlandı: ${selected?.name ?: ""}", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                RemotePad(
                    onUp = { sendKey(Remotemessage.RemoteKeyCode.KEYCODE_DPAD_UP) },
                    onDown = { sendKey(Remotemessage.RemoteKeyCode.KEYCODE_DPAD_DOWN) },
                    onLeft = { sendKey(Remotemessage.RemoteKeyCode.KEYCODE_DPAD_LEFT) },
                    onRight = { sendKey(Remotemessage.RemoteKeyCode.KEYCODE_DPAD_RIGHT) },
                    onCenter = { sendKey(Remotemessage.RemoteKeyCode.KEYCODE_DPAD_CENTER) },
                    onBack = { sendKey(Remotemessage.RemoteKeyCode.KEYCODE_BACK) },
                    onHome = { sendKey(Remotemessage.RemoteKeyCode.KEYCODE_HOME) },
                    onPlayPause = { sendKey(Remotemessage.RemoteKeyCode.KEYCODE_MEDIA_PLAY_PAUSE) },
                    onVolUp = { sendKey(Remotemessage.RemoteKeyCode.KEYCODE_VOLUME_UP) },
                    onVolDown = { sendKey(Remotemessage.RemoteKeyCode.KEYCODE_VOLUME_DOWN) }
                )
            } else {
                Text("Bir cihaza bağlanın ve uzaktan kumandayı kullanın.", style = MaterialTheme.typography.bodyMedium)
            }

            error?.let { err ->
                Spacer(Modifier.height(6.dp))
                Text("Hata: $err", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (pinNeeded) {
        PinDialog(
            onConfirm = {
                pinNeeded = false
                remote.sendSecret(pinCode.trim())
                pinCode = ""
            },
            onDismiss = { pinNeeded = false },
            onChange = { pinCode = it },
            value = pinCode
        )
    }
}

@Composable
fun RemotePad(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onCenter: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onPlayPause: () -> Unit,
    onVolUp: () -> Unit,
    onVolDown: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = onBack) { Text("Geri") }
            OutlinedButton(onClick = onHome) { Text("Ana Ekran") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FilledTonalButton(onClick = onUp, modifier = Modifier.size(96.dp)) { Text("▲") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = onLeft, modifier = Modifier.size(96.dp)) { Text("◀") }
            Button(onClick = onCenter, modifier = Modifier.size(96.dp)) { Text("OK", textAlign = TextAlign.Center) }
            FilledTonalButton(onClick = onRight, modifier = Modifier.size(96.dp)) { Text("▶") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FilledTonalButton(onClick = onDown, modifier = Modifier.size(96.dp)) { Text("▼") }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = onVolDown) { Text("Ses -") }
            OutlinedButton(onClick = onPlayPause) { Text("Oynat/Duraklat") }
            OutlinedButton(onClick = onVolUp) { Text("Ses +") }
        }
    }
}

@Composable
fun PinDialog(onConfirm: () -> Unit, onDismiss: () -> Unit, onChange: (String) -> Unit, value: String) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp)) {
                Text("Ekrandaki 6 haneli PIN’i girin", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = value, onValueChange = onChange, singleLine = true)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("İptal") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onConfirm, enabled = value.length >= 4) { Text("Bağlan") }
                }
            }
        }
    }
}

/**
 * Discover Android TV devices via mDNS (_androidtvremote._tcp)
 * Requires CHANGE_WIFI_MULTICAST_STATE to acquire a MulticastLock.
 */
fun discoverDevices(
    context: Context,
    onFound: (TvDevice) -> Unit,
    onDone: () -> Unit
) {
    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val lock = wifi.createMulticastLock("mistick-mdns").apply { setReferenceCounted(true); acquire() }
    Thread {
        try {
            val jmdns = JmDNS.create()
            val listener = object : ServiceListener {
                override fun serviceAdded(event: ServiceEvent) {
                    jmdns.requestServiceInfo(event.type, event.name, true)
                }
                override fun serviceRemoved(event: ServiceEvent) { /* ignore */ }
                override fun serviceResolved(event: ServiceEvent) {
                    val info = event.info
                    val host = info.inet4Addresses.firstOrNull() ?: info.inet6Addresses.firstOrNull()
                    if (host != null) {
                        val device = TvDevice(
                            name = info.name,
                            host = host,
                            port = info.port
                        )
                        onFound(device)
                    }
                }
            }
            jmdns.addServiceListener("_androidtvremote._tcp.local.", listener)
            Thread.sleep(5000)
            jmdns.removeServiceListener("_androidtvremote._tcp.local.", listener)
            jmdns.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { lock.release() } catch (_: Exception) {}
            onDone()
        }
    }.start()
}
