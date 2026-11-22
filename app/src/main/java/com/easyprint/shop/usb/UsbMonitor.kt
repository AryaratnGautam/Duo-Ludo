package com.easyprint.shop.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PrintServiceStatus {
    CHECKING,
    READY_TO_PRINT_VIA_SERVICE,
    HP_PLUGIN_MISSING_OR_DISABLED,
    NO_USABLE_PRINT_SERVICE
}

data class UsbConnectionState(
    val isChecking: Boolean = true,
    val isUsbHostSupported: Boolean = true,
    val attachedDeviceCount: Int = 0,
    val attachedDeviceNames: List<String> = emptyList(),
    val hpPluginInstalled: Boolean = false,
    val anyPrintServiceInstalled: Boolean = false,
    val printServiceStatus: PrintServiceStatus = PrintServiceStatus.CHECKING
) {
    val isUsbDeviceDetected: Boolean
        get() = attachedDeviceCount > 0
}

/**
 * Passively inspects USB host capability, attached USB devices, and installed print services.
 * Never claims exclusive USB ownership or requests USB permissions that belong to HP Print Service Plugin.
 */
class UsbMonitor(private val context: Context) {

    private val _state = MutableStateFlow(UsbConnectionState())
    val state: StateFlow<UsbConnectionState> = _state.asStateFlow()

    private var isRegistered = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == UsbManager.ACTION_USB_DEVICE_ATTACHED ||
                action == UsbManager.ACTION_USB_DEVICE_DETACHED
            ) {
                refresh()
            }
        }
    }

    fun startMonitoring() {
        if (!isRegistered) {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            ContextCompat.registerReceiver(
                context,
                usbReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isRegistered = true
        }
        refresh()
    }

    fun stopMonitoring() {
        if (isRegistered) {
            runCatching { context.unregisterReceiver(usbReceiver) }
            isRegistered = false
        }
    }

    fun refresh() {
        val pm = context.packageManager
        val usbHostSupported = pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        val devices: Map<String, UsbDevice> = runCatching {
            usbManager?.deviceList.orEmpty()
        }.getOrDefault(emptyMap())

        val names = devices.values.mapNotNull { device ->
            val manufacturer = runCatching { device.manufacturerName }.getOrNull()?.trim()
            val product = runCatching { device.productName }.getOrNull()?.trim()
            listOfNotNull(manufacturer, product)
                .filter { it.isNotEmpty() }
                .joinToString(" ")
                .ifEmpty { null }
        }

        val serviceCheck = inspectPrintServices(pm)

        _state.value = UsbConnectionState(
            isChecking = false,
            isUsbHostSupported = usbHostSupported,
            attachedDeviceCount = devices.size,
            attachedDeviceNames = names,
            hpPluginInstalled = serviceCheck.hpPluginInstalled,
            anyPrintServiceInstalled = serviceCheck.anyPrintServiceInstalled,
            printServiceStatus = serviceCheck.status
        )
    }

    private data class ServiceInspectionResult(
        val hpPluginInstalled: Boolean,
        val anyPrintServiceInstalled: Boolean,
        val status: PrintServiceStatus
    )

    private fun inspectPrintServices(pm: PackageManager): ServiceInspectionResult {
        val printServiceIntent = Intent("android.printservice.PrintService")
        val resolvedServices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(
                printServiceIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(printServiceIntent, PackageManager.GET_META_DATA)
        }

        val servicePackages = resolvedServices
            .mapNotNull { it.serviceInfo?.packageName }
            .toSet()

        val hpInstalled = servicePackages.contains(HP_PRINT_SERVICE_PACKAGE) ||
            isPackageInstalled(pm, HP_PRINT_SERVICE_PACKAGE)
        val anyServiceInstalled = servicePackages.isNotEmpty()

        // Check Settings.Secure if readable on this Android version to see if services are enabled.
        val enabledRaw = runCatching {
            Settings.Secure.getString(context.contentResolver, "enabled_print_services")
        }.getOrNull()

        val status = when {
            !anyServiceInstalled -> PrintServiceStatus.NO_USABLE_PRINT_SERVICE
            enabledRaw != null -> {
                val enabledPackages = enabledRaw.split(":")
                    .mapNotNull { entry -> entry.substringBefore("/").takeIf { it.isNotBlank() } }
                    .toSet()
                when {
                    enabledPackages.contains(HP_PRINT_SERVICE_PACKAGE) ->
                        PrintServiceStatus.READY_TO_PRINT_VIA_SERVICE
                    hpInstalled -> PrintServiceStatus.HP_PLUGIN_MISSING_OR_DISABLED
                    enabledPackages.isNotEmpty() ->
                        PrintServiceStatus.READY_TO_PRINT_VIA_SERVICE
                    else -> PrintServiceStatus.HP_PLUGIN_MISSING_OR_DISABLED
                }
            }
            hpInstalled -> PrintServiceStatus.READY_TO_PRINT_VIA_SERVICE
            else -> PrintServiceStatus.HP_PLUGIN_MISSING_OR_DISABLED
        }

        return ServiceInspectionResult(
            hpPluginInstalled = hpInstalled,
            anyPrintServiceInstalled = anyServiceInstalled,
            status = status
        )
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }

    companion object {
        const val HP_PRINT_SERVICE_PACKAGE = "com.hp.android.printservice"

        fun createOpenPrintSettingsIntent(): Intent {
            return Intent(Settings.ACTION_PRINT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
