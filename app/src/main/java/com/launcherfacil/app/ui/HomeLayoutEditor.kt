/*
 * LlamaditApp
 * Copyright (C) 2026 alvsanmun
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.launcherfacil.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.config.ButtonSize
import com.launcherfacil.app.config.DEFAULT_HOME_LAYOUT
import com.launcherfacil.app.config.HomeEntry
import com.launcherfacil.app.config.HomeItem
import com.launcherfacil.app.config.SosIcon

/** Proporción de la vista previa respecto a la pantalla real. */
private const val PREVIEW_SCALE = 0.42f

/**
 * Editor del diseño de la pantalla principal: vista previa en miniatura y, por cada
 * elemento, flechas para moverlo, su tamaño y si se ve. El reloj no se puede ocultar.
 */
@Composable
fun HomeLayoutEditor(layout: List<HomeEntry>, sosIcon: SosIcon, onChange: (List<HomeEntry>) -> Unit) {
    HomePreview(layout, sosIcon)

    fun move(index: Int, by: Int) {
        val target = index + by
        if (target in layout.indices) onChange(layout.toMutableList().apply { add(target, removeAt(index)) })
    }
    fun update(index: Int, entry: HomeEntry) = onChange(layout.toMutableList().apply { set(index, entry) })

    layout.forEachIndexed { index, entry ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemBadge(entry.item, sosIcon)
                Text(
                    stringResource(entry.item.label()),
                    color = if (entry.visible) TextoClaro else TextoSuave,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                )
                MoveButton(R.drawable.ic_arrow_up, index > 0) { move(index, -1) }
                Spacer(Modifier.width(8.dp))
                MoveButton(R.drawable.ic_arrow_down, index < layout.lastIndex) { move(index, +1) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.item.isButton) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ButtonSize.entries.forEach { size ->
                            SizeChip(stringResource(size.label()), entry.size == size, entry.visible) {
                                update(index, entry.copy(size = size))
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (entry.item == HomeItem.CLOCK) {
                    Text(stringResource(R.string.settings_home_always_visible), color = TextoSuave, fontSize = 14.sp)
                } else {
                    Switch(checked = entry.visible, onCheckedChange = { update(index, entry.copy(visible = it)) })
                }
            }
        }
    }

    if (layout != DEFAULT_HOME_LAYOUT) {
        TextButtonLike(stringResource(R.string.settings_home_reset)) { onChange(DEFAULT_HOME_LAYOUT) }
    }
}

/** La pantalla principal de verdad, dibujada en pequeño y sin responder a los toques. */
@Composable
private fun HomePreview(layout: List<HomeEntry>, sosIcon: SosIcon) {
    val config = LocalConfiguration.current
    val width = config.screenWidthDp.dp
    val height = config.screenHeightDp.dp
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width * PREVIEW_SCALE, height * PREVIEW_SCALE)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, GrisBoton, RoundedCornerShape(12.dp))
        ) {
            Box(
                Modifier
                    .requiredSize(width, height)
                    .graphicsLayer {
                        scaleX = PREVIEW_SCALE
                        scaleY = PREVIEW_SCALE
                    }
            ) {
                HomeScreen(layout = layout, sosIcon = sosIcon, newSms = 1, newMissedCalls = 2)
            }
            // Capa transparente encima: se traga los toques (el reloj abriría el PIN).
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            )
        }
    }
}

/** Selector del dibujo del botón SOS: seis botones rojos, el elegido con un marco. */
@Composable
fun SosIconPicker(selected: SosIcon, onSelect: (SosIcon) -> Unit) {
    SosIcon.entries.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { icon ->
                val shape = RoundedCornerShape(20.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(shape)
                        .background(RojoSos)
                        .then(
                            if (icon == selected) Modifier.border(5.dp, TextoClaro, shape)
                            else Modifier
                        )
                        .clickable { onSelect(icon) }
                ) {
                    SosFace(icon)
                }
            }
        }
    }
}

@Composable
private fun ItemBadge(item: HomeItem, sosIcon: SosIcon) {
    val (color, icon) = when (item) {
        HomeItem.CLOCK -> GrisBoton to null
        HomeItem.BATTERY -> GrisBoton to null
        HomeItem.CALL -> VerdeLlamar to R.drawable.ic_call
        HomeItem.WHATSAPP -> VerdeWhatsApp to R.drawable.ic_whatsapp_video
        HomeItem.DIALPAD -> AzulTeclado to R.drawable.ic_dialpad
        HomeItem.SOS -> RojoSos to null
        HomeItem.MESSAGES -> NaranjaMensajes to R.drawable.ic_sms
        HomeItem.MISSED_CALLS -> MoradoPerdidas to R.drawable.ic_phone_missed
        HomeItem.GO_HOME -> MarronCasa to R.drawable.ic_go_home
    }
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(color),
        contentAlignment = Alignment.Center
    ) {
        when (item) {
            HomeItem.CLOCK -> Text("12:00", color = TextoClaro, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            HomeItem.BATTERY -> Text("%", color = TextoClaro, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            HomeItem.SOS -> SosFace(sosIcon)
            else -> BadgeIcon(icon!!)
        }
    }
}

@Composable
private fun BadgeIcon(@DrawableRes id: Int) {
    Icon(painterResource(id), contentDescription = null, tint = TextoClaro, modifier = Modifier.size(30.dp))
}

@Composable
private fun SizeChip(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(0xFF90CAF9) else GrisBoton)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color.Black else if (enabled) TextoClaro else TextoSuave,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun TextButtonLike(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .border(2.dp, GrisBoton, RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextoClaro, fontSize = 17.sp)
    }
}

@StringRes
private fun HomeItem.label(): Int = when (this) {
    HomeItem.CLOCK -> R.string.home_item_clock
    HomeItem.BATTERY -> R.string.home_item_battery
    HomeItem.CALL -> R.string.home_item_call
    HomeItem.WHATSAPP -> R.string.home_item_whatsapp
    HomeItem.DIALPAD -> R.string.home_item_dialpad
    HomeItem.SOS -> R.string.home_item_sos
    HomeItem.MESSAGES -> R.string.home_item_messages
    HomeItem.MISSED_CALLS -> R.string.home_item_missed
    HomeItem.GO_HOME -> R.string.home_item_go_home
}

@StringRes
private fun ButtonSize.label(): Int = when (this) {
    ButtonSize.SMALL -> R.string.size_small
    ButtonSize.MEDIUM -> R.string.size_medium
    ButtonSize.LARGE -> R.string.size_large
}
