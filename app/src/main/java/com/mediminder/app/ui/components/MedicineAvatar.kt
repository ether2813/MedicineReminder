package com.mediminder.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MedicationLiquid
import androidx.compose.material.icons.rounded.Sanitizer
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mediminder.app.data.model.MedicineForm
import com.mediminder.app.ui.theme.medicineColor

fun formIcon(form: MedicineForm): ImageVector? = when (form) {
    MedicineForm.TABLET, MedicineForm.CAPSULE -> null // drawn by hand below
    MedicineForm.SYRUP -> Icons.Rounded.MedicationLiquid
    MedicineForm.INJECTION -> Icons.Rounded.Vaccines
    MedicineForm.DROPS -> Icons.Rounded.WaterDrop
    MedicineForm.INHALER -> Icons.Rounded.Air
    MedicineForm.CREAM -> Icons.Rounded.Spa
    MedicineForm.SPRAY -> Icons.Rounded.Sanitizer
    MedicineForm.PATCH -> Icons.Rounded.Healing
    MedicineForm.POWDER -> Icons.Rounded.Grain
    MedicineForm.SUPPOSITORY -> Icons.Rounded.LocalPharmacy
    MedicineForm.OTHER -> Icons.Rounded.MedicalServices
}

@Composable
fun MedicineAvatar(
    form: MedicineForm,
    colorIndex: Int,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val tint = medicineColor(colorIndex)
    Box(
        modifier = modifier
            .size(size)
            .background(tint.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        FormGlyph(form, tint, size * 0.5f)
    }
}

@Composable
fun FormGlyph(form: MedicineForm, tint: Color, size: Dp) {
    val icon = formIcon(form)
    when {
        icon != null -> Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
        form == MedicineForm.CAPSULE -> CapsuleGlyph(tint, size)
        else -> TabletGlyph(tint, size)
    }
}

@Composable
private fun CapsuleGlyph(tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height * 0.42f
        val top = (this.size.height - h) / 2
        rotate(-45f) {
            val radius = CornerRadius(h / 2, h / 2)
            clipRect(right = w / 2) {
                drawRoundRect(tint, Offset(0f, top), Size(w, h), radius)
            }
            clipRect(left = w / 2) {
                drawRoundRect(tint.copy(alpha = 0.45f), Offset(0f, top), Size(w, h), radius)
            }
        }
    }
}

@Composable
private fun TabletGlyph(tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2 * 0.92f
        drawCircle(tint, r)
        drawCircle(Color.White.copy(alpha = 0.35f), r * 0.78f, style = Stroke(width = r * 0.08f))
        rotate(-45f) {
            drawLine(
                Color.White.copy(alpha = 0.9f),
                start = Offset(center.x - r * 0.55f, center.y),
                end = Offset(center.x + r * 0.55f, center.y),
                strokeWidth = r * 0.16f,
            )
        }
    }
}
