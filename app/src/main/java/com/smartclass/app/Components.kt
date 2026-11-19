package com.smartclass.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ScreenFrame(title: String, subtitle: String, wide: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Canvas)) {
        Row(Modifier.fillMaxWidth().background(Navy).padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SMARTCLASS  /  GESTIÓN ESCOLAR", color = Color(0xFF8DE4D5), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color(0xFFCBD5E1), fontSize = 13.sp)
            }
            Icon(Icons.Default.School, "SmartClass", tint = Color(0xFF8DE4D5), modifier = Modifier.size(28.dp))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 28.dp else 18.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
internal fun SectionTitle(title: String, subtitle: String? = null) {
    Column {
        Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
internal fun MetricCard(title: String, value: String, caption: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(18.dp)).padding(17.dp)) {
        Icon(icon, null, tint = Teal, modifier = Modifier.size(22.dp)); Spacer(Modifier.height(10.dp))
        Text(title, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = Navy, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(caption, color = Muted, fontSize = 11.sp)
    }
}

@Composable
internal fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
internal fun StatusPill(label: String, active: Boolean) {
    Row(Modifier.background(if (active) Color(0xFFE1F5F1) else Color(0xFFE8ECF2), RoundedCornerShape(50)).padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(if (active) Teal else Muted, CircleShape)); Spacer(Modifier.width(7.dp)); Text("$label · ${if (active) "Activo" else "Inactivo"}", color = Ink, fontSize = 11.sp)
    }
}
