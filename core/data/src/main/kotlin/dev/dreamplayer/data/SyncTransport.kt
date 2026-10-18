package dev.dreamplayer.data

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import java.io.Closeable
import java.io.FileInputStream
import java.io.IOException
import java.net.BindException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** the sync port (the TUI uses the same one). */
const val DEFAULT_SYNC_PORT = 47800

/** how to reach the other device. Every transport is TCP. */
sealed interface SyncTarget {
    /** wait for the other device on [port] (the same Wi-Fi, or a PC over USB: `adb`). */
    data class Listen(val port: Int = DEFAULT_SYNC_PORT) : SyncTarget

    /** connect to `host[:port]` of a device that listens. */
    data class Connect(val address: String) : SyncTarget

    /** start a local-only Wi-Fi hotspot and wait on it. */
    data class HotspotHost(val port: Int = DEFAULT_SYNC_PORT) : SyncTarget

    /** join the hotspot of the other device and connect to it. */
    data class HotspotJoin(val ssid: String, val password: String, val port: Int = DEFAULT_SYNC_PORT) : SyncTarget

    /**
     * a USB cable without adb / debug mode: the computer switches the phone
     * to an accessory of Dream Player (TUI `:sync usb`, Android Open
     * Accessory); this side is the host.
     */
    data object UsbCable : SyncTarget
}

/** the network of a started hotspot, to show to the other device's user. */
data class HotspotInfo(val ssid: String, val password: String)

/** what the transport tells the interface while it comes up. */
internal interface TransportUi {
    fun info(text: String)
    /** what the transport waits for now. */
    fun stage(text: String)
    fun hotspot(info: HotspotInfo)
    fun listening(addresses: List<String>, port: Int)
}

/**
 * a connected transport: whether this side listened (the host of the sync
 * session), the descriptor the core writes the outgoing bytes into, and
 * reading with a short wait (so the session can be cancelled). Closing it
 * also releases the hotspot / network request / accessory.
 */
internal sealed class Link(val host: Boolean) : Closeable {
    abstract val outFd: Int

    /** received bytes into [buf]: the count, 0 — nothing within ~[POLL_MS], -1 — closed. */
    abstract fun read(buf: ByteArray): Int

    companion object {
        const val POLL_MS = 200
    }
}

internal class SocketLink(private val socket: Socket, host: Boolean, private val release: () -> Unit) : Link(host) {
    /** a duplicate of the socket's descriptor. */
    private val out = ParcelFileDescriptor.fromSocket(socket)
    private val input = socket.apply { soTimeout = POLL_MS }.getInputStream()

    override val outFd: Int get() = out.fd

    override fun read(buf: ByteArray): Int = try {
        input.read(buf)
    } catch (_: SocketTimeoutException) {
        0
    }

    override fun close() {
        runCatching { out.close() }
        runCatching { socket.close() }
        runCatching { release() }
    }
}

/**
 * the accessory interface opened by [UsbManager.openAccessory]. A read of it
 * can't time out, so a reader thread hands the received chunks over.
 */
internal class AccessoryLink(private val pfd: ParcelFileDescriptor) : Link(host = true) {
    private val chunks = LinkedBlockingQueue<ByteArray>()
    private var leftover: ByteArray? = null
    private var leftoverAt = 0

    init {
        thread(name = "usb-accessory-read", isDaemon = true) {
            val input = FileInputStream(pfd.fileDescriptor)
            val buf = ByteArray(ACCESSORY_READ)
            try {
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0) chunks.put(buf.copyOf(n))
                }
            } catch (_: IOException) {
                // closed or unplugged.
            }
            chunks.put(EOF)
        }
    }

    override val outFd: Int get() = pfd.fd

    override fun read(buf: ByteArray): Int {
        val chunk = leftover ?: chunks.poll(POLL_MS.toLong(), TimeUnit.MILLISECONDS) ?: return 0
        if (chunk === EOF) return -1.also { chunks.put(EOF) }
        val n = minOf(buf.size, chunk.size - leftoverAt)
        chunk.copyInto(buf, 0, leftoverAt, leftoverAt + n)
        leftoverAt += n
        if (leftoverAt == chunk.size) {
            leftover = null
            leftoverAt = 0
        } else {
            leftover = chunk
        }
        return n
    }

    override fun close() {
        runCatching { pfd.close() }
    }

    private companion object {
        /** the accessory driver reads in 16 KiB requests. */
        const val ACCESSORY_READ = 16 * 1024
        val EOF = ByteArray(0)
    }
}

/** brings transports up; every wait is cancellable with the coroutine. */
internal class SyncTransport(private val context: Context) {
    private val wifi get() = context.applicationContext.getSystemService(WifiManager::class.java)
    private val connectivity get() = context.getSystemService(ConnectivityManager::class.java)

    suspend fun establish(target: SyncTarget, ui: TransportUi): Link = when (target) {
        is SyncTarget.Listen -> accept(target.port, ui, release = {})
        is SyncTarget.Connect -> {
            val (host, port) = parseAddress(target.address)
            ui.info("подключение к $host:$port…")
            SocketLink(connect(Socket(), InetSocketAddress(host, port)), host = false, release = {})
        }
        is SyncTarget.HotspotHost -> {
            ui.info("запуск точки доступа…")
            val reservation = startHotspot()
            try {
                ui.hotspot(hotspotInfo(reservation))
                accept(target.port, ui, release = { reservation.close() })
            } catch (e: Throwable) {
                reservation.close()
                throw e
            }
        }
        is SyncTarget.HotspotJoin -> join(target, ui)
        SyncTarget.UsbCable -> accessory(ui)
    }

    // ───────────────────────── TCP ─────────────────────────

    /** waits for one connection on [port]; this side is the host. */
    private suspend fun accept(port: Int, ui: TransportUi, release: () -> Unit): Link {
        val server = ServerSocket().apply {
            reuseAddress = true
            soTimeout = POLL_MS
            try {
                bind(InetSocketAddress(port))
            } catch (e: BindException) {
                close()
                throw IOException("порт $port занят другим приложением: выберите другой", e)
            }
        }
        server.use {
            ui.listening(localAddresses(), port)
            ui.info("ожидание подключения на порту $port…")
            while (true) {
                currentCoroutineContext().ensureActive()
                try {
                    val socket = server.accept()
                    socket.tcpNoDelay = true
                    ui.info("подключено: ${socket.inetAddress.hostAddress}")
                    return SocketLink(socket, host = true, release = release)
                } catch (_: SocketTimeoutException) {
                    // check for cancellation and wait again.
                }
            }
        }
    }

    private fun connect(socket: Socket, address: InetSocketAddress): Socket = socket.apply {
        try {
            connect(address, CONNECT_TIMEOUT_MS)
            tcpNoDelay = true
        } catch (e: IOException) {
            close()
            throw IOException("не удалось подключиться к ${address.hostString}:${address.port}: ${e.message}", e)
        }
    }

    // ───────────────────────── USB accessory ─────────────────────────

    /** waits for the computer to make this phone its accessory, then opens it. */
    private suspend fun accessory(ui: TransportUi): Link {
        val usb = context.getSystemService(UsbManager::class.java)
            ?: throw IOException("USB-аксессуары не поддерживаются этим телефоном")
        ui.stage("ожидание USB-кабеля")
        ui.info("подключите кабель и запустите обмен по USB на другом устройстве…")
        var accessory: UsbAccessory? = null
        while (accessory == null) {
            currentCoroutineContext().ensureActive()
            accessory = usb.accessoryList?.firstOrNull { it.manufacturer == ACCESSORY_MANUFACTURER && it.model == ACCESSORY_MODEL }
            if (accessory == null) delay(POLL_MS.toLong())
        }
        if (!usb.hasPermission(accessory) && !requestPermission(usb, accessory)) {
            throw IOException("доступ к USB-аксессуару не разрешён")
        }
        val pfd = usb.openAccessory(accessory) ?: throw IOException("не удалось открыть USB-аксессуар (переподключите кабель)")
        ui.info("подключено по USB")
        return AccessoryLink(pfd)
    }

    /** asks the user for the accessory; true — granted. */
    private suspend fun requestPermission(usb: UsbManager, accessory: UsbAccessory): Boolean =
        suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    runCatching { context.unregisterReceiver(this) }
                    if (cont.isActive) cont.resume(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_USB_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED)
            cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
            // the system fills in the result extras: the intent must be mutable (explicit, so safe).
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
            usb.requestPermission(accessory, PendingIntent.getBroadcast(context, 0, intent, flags))
        }

    // ───────────────────────── hotspot ─────────────────────────

    /** a local-only hotspot (no internet sharing); closed with the reservation. */
    @SuppressLint("MissingPermission") // requested by the interface before starting.
    private suspend fun startHotspot(): WifiManager.LocalOnlyHotspotReservation = suspendCancellableCoroutine { cont ->
        val callback = object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                if (cont.isActive) cont.resume(reservation) else reservation.close()
            }

            override fun onFailed(reason: Int) {
                if (cont.isActive) cont.resumeWithException(IOException("точка доступа не запустилась: ${hotspotFailure(reason)}"))
            }
        }
        try {
            wifi.startLocalOnlyHotspot(callback, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            cont.resumeWithException(IOException("нет разрешения на точку доступа", e))
        } catch (e: IllegalStateException) {
            // another app's local-only hotspot is already running.
            cont.resumeWithException(IOException("точка доступа уже запущена другим приложением", e))
        }
    }

    @Suppress("DEPRECATION")
    private fun hotspotInfo(r: WifiManager.LocalOnlyHotspotReservation): HotspotInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val c = r.softApConfiguration
            val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) c.wifiSsid?.toString() else c.ssid
            HotspotInfo(ssid.orEmpty().removeSurrounding("\""), c.passphrase.orEmpty())
        } else {
            val c = r.wifiConfiguration
            HotspotInfo(c?.SSID.orEmpty().removeSurrounding("\""), c?.preSharedKey.orEmpty().removeSurrounding("\""))
        }

    private fun hotspotFailure(reason: Int) = when (reason) {
        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "нет свободного канала"
        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED -> "раздача запрещена на устройстве"
        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> "несовместимый режим Wi-Fi (выключите обычную раздачу)"
        else -> "ошибка $reason"
    }

    /** joins the other device's hotspot (Android 10+) and connects to its gateway. */
    private suspend fun join(target: SyncTarget.HotspotJoin, ui: TransportUi): Link {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw IOException("на Android 9 и ниже подключитесь к сети «${target.ssid}» в настройках Wi-Fi и выберите «По адресу»")
        }
        ui.info("подключение к сети «${target.ssid}» (подтвердите в системном окне)…")
        val spec = WifiNetworkSpecifier.Builder().setSsid(target.ssid).setWpa2Passphrase(target.password).build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(spec)
            .build()
        var callback: ConnectivityManager.NetworkCallback? = null
        val release = { callback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }; Unit }
        try {
            val network = suspendCancellableCoroutine { cont ->
                val cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (cont.isActive) cont.resume(network)
                    }

                    override fun onUnavailable() {
                        if (cont.isActive) cont.resumeWithException(IOException("сеть «${target.ssid}» недоступна или подключение отклонено"))
                    }
                }
                callback = cb
                connectivity.requestNetwork(request, cb, JOIN_TIMEOUT_MS)
            }
            val gateway = withTimeoutOrNull(GATEWAY_WAIT_MS) {
                var gw: InetAddress? = null
                while (gw == null) {
                    gw = connectivity.getLinkProperties(network)?.let(::gatewayOf)
                    if (gw == null) delay(POLL_MS.toLong())
                }
                gw
            } ?: throw IOException("не удалось узнать адрес точки доступа: выберите «По адресу»")
            ui.info("подключение к ${gateway.hostAddress}:${target.port}…")
            val socket = connect(network.socketFactory.createSocket(), InetSocketAddress(gateway, target.port))
            return SocketLink(socket, host = false, release = release)
        } catch (e: Throwable) {
            release()
            throw e
        }
    }

    /** the hotspot's address: its DHCP server / default gateway, else `x.y.z.1` of our address. */
    private fun gatewayOf(lp: LinkProperties): InetAddress? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) lp.dhcpServerAddress?.let { return it }
        lp.routes.firstOrNull { it.gateway is Inet4Address && !it.gateway!!.isAnyLocalAddress }?.gateway?.let { return it }
        val own = lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address } ?: return null
        val b = own.address.copyOf().also { it[3] = 1 }
        return InetAddress.getByAddress(b)
    }

    private companion object {
        const val POLL_MS = Link.POLL_MS
        const val ACTION_USB_PERMISSION = "dev.dreamplayer.USB_PERMISSION"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val JOIN_TIMEOUT_MS = 60_000
        const val GATEWAY_WAIT_MS = 10_000L
    }
}

/** the identity the computer gives this phone as an accessory (TUI `sync/usb.rs`). */
const val ACCESSORY_MANUFACTURER = "DreamPlayer"
const val ACCESSORY_MODEL = "DreamPlayerSync"

/** `host[:port]` -> host and port (the port defaults to [DEFAULT_SYNC_PORT]). */
internal fun parseAddress(s: String): Pair<String, Int> {
    val text = s.trim()
    val host = text.substringBeforeLast(':', text)
    val port = text.substringAfterLast(':', "").toIntOrNull()
    if (host.isEmpty()) throw IOException("укажите адрес устройства")
    return if (port != null && port in 1..65535) host to port else text to DEFAULT_SYNC_PORT
}

/** IPv4 addresses of this device the other one can connect to. */
internal fun localAddresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { nif -> nif.inetAddresses.toList().filterIsInstance<Inet4Address>().map { "${it.hostAddress} (${nif.name})" } }
}.getOrDefault(emptyList())
