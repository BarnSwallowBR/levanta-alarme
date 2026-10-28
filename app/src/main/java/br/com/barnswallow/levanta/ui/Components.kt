package br.com.barnswallow.levanta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Botão principal com o gradiente do nascer do sol. */
@Composable
fun SunriseButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 60.dp,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(Dawn.sunrise, shape)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Dawn.OnSun, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text,
                style = if (height >= 68.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Dawn.OnSun,
            )
        }
    }
}

/** Cabeçalho das telas: título grande, voltar opcional e ações à direita. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = if (onBack == null) 20.dp else 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** Bloco de conteúdo com título, usado para agrupar ajustes. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

/** Pílula selecionável grande (48dp), para escolhas curtas como soneca e atalhos de dias. */
@Composable
fun PillChoice(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = CircleShape
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) colors.primary else colors.surfaceContainerHigh, shape)
            .border(1.dp, if (selected) Color.Transparent else colors.outlineVariant, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onPrimary else colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Linha de pílulas de largura igual. */
@Composable
fun <T> PillRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            PillChoice(label, selected == value, { onSelect(value) }, Modifier.weight(1f))
        }
    }
}

/** Cartão grande selecionável com ícone, título e descrição (missões, dificuldade). */
@Composable
fun ChoiceTile(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    badge: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh, shape)
            .border(2.dp, if (selected) colors.primary else Color.Transparent, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (selected) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) colors.onPrimaryContainer else colors.onSurface)
        }
        if (badge != null) {
            Spacer(Modifier.height(10.dp))
            Text(badge, style = ClockStyle.copy(fontSize = 20.sp, letterSpacing = 0.sp), maxLines = 1, color = if (selected) colors.primary else colors.onSurface)
        }
        Spacer(Modifier.height(6.dp))
        Text(description, style = MaterialTheme.typography.bodySmall, color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
    }
}

/** Sete letras dos dias, acesas nos dias em que o alarme toca. */
@Composable
fun DayDots(days: Set<Int>, enabled: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        br.com.barnswallow.levanta.data.Alarm.DAY_LETTERS.forEachIndexed { index, letter ->
            val on = (index + 1) in days
            Text(
                letter,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    on && enabled -> MaterialTheme.colorScheme.primary
                    on -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.outline
                },
                fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Normal,
            )
        }
    }
}
