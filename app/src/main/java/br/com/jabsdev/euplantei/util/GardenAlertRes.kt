package br.com.jabsdev.euplantei.util

import androidx.annotation.StringRes
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.domain.GardenAlertKind

/** Recursos de texto compartilhados entre a UI do jardim e a notificação. */
@get:StringRes
val GardenAlertKind.titleRes: Int
    get() = when (this) {
        GardenAlertKind.RAIN -> R.string.garden_alert_rain_title
        GardenAlertKind.HEAT -> R.string.garden_alert_heat_title
        GardenAlertKind.DRY_AIR -> R.string.garden_alert_dry_title
        GardenAlertKind.COLD -> R.string.garden_alert_cold_title
        GardenAlertKind.WIND -> R.string.garden_alert_wind_title
    }

@get:StringRes
val GardenAlertKind.bodyRes: Int
    get() = when (this) {
        GardenAlertKind.RAIN -> R.string.garden_alert_rain_body
        GardenAlertKind.HEAT -> R.string.garden_alert_heat_body
        GardenAlertKind.DRY_AIR -> R.string.garden_alert_dry_body
        GardenAlertKind.COLD -> R.string.garden_alert_cold_body
        GardenAlertKind.WIND -> R.string.garden_alert_wind_body
    }
