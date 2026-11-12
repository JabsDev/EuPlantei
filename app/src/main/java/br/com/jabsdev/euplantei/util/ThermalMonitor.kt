package br.com.jabsdev.euplantei.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * O Galaxy S22 reduz a frequência da CPU sob calor. Lemos a temperatura da
 * bateria pelo broadcast sticky ACTION_BATTERY_CHANGED (sem permissão especial).
 * 40 °C: aviso; 43 °C: geração bloqueada.
 */
class ThermalMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val _temperatureC = MutableStateFlow(readStickyTemperature())
    val temperatureC: StateFlow<Float?> = _temperatureC.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { _temperatureC.value = extractTemperature(it) }
        }
    }

    init {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sticky?.let { _temperatureC.value = extractTemperature(it) }
    }

    val isHot: Boolean get() = (_temperatureC.value ?: 0f) >= HOT_THRESHOLD_C
    val isCritical: Boolean get() = (_temperatureC.value ?: 0f) >= CRITICAL_THRESHOLD_C

    fun close() {
        runCatching { appContext.unregisterReceiver(receiver) }
    }

    private fun readStickyTemperature(): Float? {
        val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.let(::extractTemperature)
    }

    private fun extractTemperature(intent: Intent): Float? {
        val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (tenths == Int.MIN_VALUE) return null
        return tenths / 10f
    }

    companion object {
        const val HOT_THRESHOLD_C = 40f
        const val CRITICAL_THRESHOLD_C = 43f
    }
}
