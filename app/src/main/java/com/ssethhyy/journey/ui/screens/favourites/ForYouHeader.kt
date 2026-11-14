/*
 * Feature Module: For You
 * File: ForYouHeader.kt
 * Description: The Greeting widget: a big thin temperature and weather icon, a greeting and today's date.
 *
 * Drawn from the for-you-page-top design: the temperature is Google Sans Flex at its thinnest, narrowest and
 * largest optical size, the greeting bold and wide. After a few seconds it collapses into a single bar (temperature
 * and icon on the left, the date on the right) so the rest of the page moves up; tap that line to open it again.
 */

package com.ssethhyy.journey.ui.screens.favourites

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.ssethhyy.journey.data.foryou.DateStyle
import com.ssethhyy.journey.data.weather.TemperatureUnit
import com.ssethhyy.journey.data.weather.WeatherCondition
import com.ssethhyy.journey.data.weather.WeatherException
import com.ssethhyy.journey.data.weather.WeatherNow
import com.ssethhyy.journey.ui.LoadState
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.theme.FlexAxes
import com.ssethhyy.journey.ui.theme.FlexType
import com.ssethhyy.journey.ui.theme.single
import com.ssethhyy.journey.utils.HapticUtil
import com.ssethhyy.journey.utils.TimeFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private val MELBOURNE: ZoneId = ZoneId.of("Australia/Melbourne")

/** The weather icon for a condition, with night versions of clear and partly cloudy. */
val WeatherCondition.icon: ImageVector
    @Composable get() = when (this) {
        WeatherCondition.CLEAR -> Sym.WeatherSunny
        WeatherCondition.PARTLY_CLOUDY -> Sym.WeatherPartlyCloudyDay
        WeatherCondition.CLOUDY -> Sym.WeatherCloud
        WeatherCondition.FOG -> Sym.WeatherFoggy
        WeatherCondition.DRIZZLE -> Sym.WeatherRainyLight
        WeatherCondition.RAIN -> Sym.WeatherRainy
        WeatherCondition.HEAVY_RAIN -> Sym.WeatherRainyHeavy
        WeatherCondition.STORM -> Sym.WeatherThunderstorm
        WeatherCondition.SNOW -> Sym.WeatherSnowy
    }

@Composable
fun WeatherNow.icon(): ImageVector = when {
    !isDay && condition == WeatherCondition.CLEAR -> Sym.WeatherClearNight
    !isDay && condition == WeatherCondition.PARTLY_CLOUDY -> Sym.WeatherPartlyCloudyNight
    else -> condition.icon
}

object ForYouDates {
    /** "Good morning, Seth", or "Good morning" with no name. */
    fun greeting(name: String, now: Instant): String {
        val clean = name.trim()
        val hello = TimeFormatter.greeting(now)
        return if (clean.isEmpty()) hello else "$hello, $clean"
    }

    fun format(style: DateStyle, now: Instant, locale: Locale = Locale.getDefault(), zone: ZoneId = MELBOURNE): String {
        val date = now.atZone(zone)
        val weekday = date.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale)
        val month = date.month.getDisplayName(JavaTextStyle.FULL, locale)
        return when (style) {
            DateStyle.SENTENCE -> "Today is the ${TimeFormatter.ordinal(date.dayOfMonth)} of $month ${date.year}"
            DateStyle.WEEKDAY -> "$weekday ${date.dayOfMonth} $month"
            DateStyle.SHORT -> "${date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale)} ${date.dayOfMonth} ${date.month.getDisplayName(JavaTextStyle.SHORT, locale)}"
            DateStyle.NUMERIC -> date.format(DateTimeFormatter.ofPattern("dd/MM/yy", locale))
        }
    }
}

private val TemperatureFamily = FlexType.single(FlexAxes(opticalSize = 144f, width = 40f), weight = 220)
private val GreetingFamily = FlexType.single(FlexAxes(opticalSize = 32f, width = 125f), weight = 700)
private val DateFamily = FlexType.single(FlexAxes(opticalSize = 16f, width = 118f), weight = 420)

@Composable
fun ForYouHeader(
    greeting: String?,
    date: String?,
    weather: LoadState<WeatherNow>?,
    unit: TemperatureUnit,
    showDetails: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onSetUpWeather: () -> Unit,
    onRetryWeather: () -> Unit,
    modifier: Modifier = Modifier,
    /** The time in the middle of the collapsed bar (null leaves it out). */
    barTime: String? = null,
    /** The date on the right of the collapsed bar (null leaves it out). */
    barDate: String? = null,
    /** Whether the collapsed bar shows the weather. */
    barWeather: Boolean = true,
    /** The temperature's size while expanded. */
    temperatureSp: Float = 116f,
    /** Set while no name is saved: tapping the greeting asks for one. */
    onAddName: (() -> Unit)? = null,
) {
    val view = LocalView.current
    val springy by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "header_expand",
    )
    // The spring overshoots a little; sizes and padding can't go below zero.
    val progress = springy.coerceIn(0f, 1f)
    val reading = (weather as? LoadState.Success)?.data
    val onSurface = MaterialTheme.colorScheme.onSurface
    val bigSize = temperatureSp.sp
    val iconScale = temperatureSp / 116f
    // The weather fades out of the collapsed bar when it isn't wanted there.
    val weatherAlpha = if (barWeather) 1f else progress

    Column(
        modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = if (expanded) "Collapse the greeting" else "Expand the greeting",
            ) {
                HapticUtil.performUIHaptic(view)
                onExpandedChange(!expanded)
            }
            .padding(top = lerp(0.dp, 4.dp, progress), bottom = lerp(0.dp, 8.dp, progress)),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            if (weatherAlpha > 0.01f) Row(
                Modifier.align(Alignment.CenterStart).alpha(weatherAlpha).height(IntrinsicSize.Min),
            ) {
                when {
                    weather == null -> Unit
                    reading != null -> {
                        Text(
                            unit.format(reading.temperatureC),
                            style = TextStyle(
                                fontFamily = TemperatureFamily,
                                fontWeight = FontWeight(220),
                                fontSize = lerp(32.sp, bigSize, progress),
                                // Trimmed to the figures, so the greeting sits close underneath like the design.
                                lineHeight = lerp(32.sp, bigSize * 0.81f, progress),
                                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                                letterSpacing = (-0.02).em,
                            ),
                            color = onSurface,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .semantics { contentDescription = "${unit.format(reading.temperatureC)}, ${reading.description}" }
                                // Figures have no descenders: drop the empty space under them so the greeting sits close.
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    val trim = (placeable.height * 0.16f * progress).toInt()
                                    layout(placeable.width, placeable.height - trim) { placeable.place(0, 0) }
                                },
                        )
                        Spacer(Modifier.width(lerp(6.dp, 22.dp * iconScale, progress)))
                        // Centred beside the temperature.
                        Icon(
                            reading.icon(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .size(lerp(24.dp, 60.dp * iconScale, progress)),
                        )
                    }
                    weather is LoadState.Error && weather.error is WeatherException.MissingKey -> WeatherChip(
                        text = "Set up weather",
                        icon = Sym.WeatherPartlyCloudyDay,
                        onClick = onSetUpWeather,
                    )
                    weather is LoadState.Error -> WeatherChip(
                        text = if (weather.error is WeatherException.Unauthorized) "Weather key not accepted" else "Weather unavailable",
                        icon = Sym.CloudOff,
                        onClick = if (weather.error is WeatherException.Unauthorized) onSetUpWeather else onRetryWeather,
                    )
                    else -> Text(
                        "--°",
                        style = TextStyle(
                            fontFamily = TemperatureFamily,
                            fontWeight = FontWeight(220),
                            fontSize = lerp(32.sp, bigSize, progress),
                            lineHeight = lerp(32.sp, bigSize * 0.81f, progress),
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                        ),
                        color = onSurface.copy(alpha = 0.3f),
                    )
                }
            }
            // The collapsed bar: the time exactly in the middle, the date and the Edit pill on the right.
            Row(Modifier.fillMaxWidth().height(48.dp).align(Alignment.TopCenter), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (barTime != null && progress < 0.98f) BarText(barTime, Modifier.alpha(1f - progress).widthIn(max = 120.dp))
                Row(Modifier.weight(1f).padding(start = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (barDate != null && progress < 0.98f) {
                        BarText(barDate, Modifier.alpha(1f - progress).weight(1f, fill = false))
                        Spacer(Modifier.width(10.dp))
                    }
                    EditButton(onEdit)
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
        ) {
            Column(Modifier.padding(top = 2.dp)) {
                if (showDetails && reading != null) {
                    val details = listOfNotNull(
                        reading.description,
                        reading.feelsLikeC?.let { "feels ${unit.format(it)}" },
                        if (reading.highC != null && reading.lowC != null) "H ${unit.format(reading.highC)} L ${unit.format(reading.lowC)}" else null,
                    ).joinToString(" · ")
                    Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(10.dp))
                }
                if (greeting != null) {
                    // One line like the design: longer greetings ("Good afternoon, Alexandra") step down in size.
                    BasicText(
                        greeting,
                        style = TextStyle(fontFamily = GreetingFamily, fontWeight = FontWeight(700), fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.01).em, color = onSurface),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 30.sp, stepSize = 1.sp),
                        modifier = if (onAddName != null) {
                            Modifier.clickable(onClickLabel = "Add your name") {
                                HapticUtil.performUIHaptic(view)
                                onAddName()
                            }
                        } else Modifier,
                    )
                }
                if (date != null) {
                    Text(
                        date,
                        style = TextStyle(fontFamily = DateFamily, fontWeight = FontWeight(420), fontSize = 16.sp, lineHeight = 22.sp),
                        color = onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** The time or date in the collapsed bar, shrinking to fit when all three are shown. */
@Composable
private fun BarText(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text,
        style = TextStyle(fontFamily = DateFamily, fontWeight = FontWeight(420), fontSize = 22.sp, lineHeight = 26.sp, color = MaterialTheme.colorScheme.onSurface),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 22.sp, stepSize = 1.sp),
        modifier = modifier,
    )
}

@Composable
private fun WeatherChip(text: String, icon: ImageVector, onClick: () -> Unit) {
    val view = LocalView.current
    AssistChip(
        onClick = {
            HapticUtil.performUIHaptic(view)
            onClick()
        },
        label = { Text(text) },
        leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize)) },
        shape = CircleShape,
    )
}

/** The Edit pill that opens edit mode, at the top right of For You. */
@Composable
fun EditButton(onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    FilledTonalButton(
        onClick = {
            HapticUtil.performUIHaptic(view)
            onEdit()
        },
        contentPadding = PaddingValues(horizontal = 18.dp),
        modifier = modifier.height(40.dp),
    ) {
        Text("Edit", style = MaterialTheme.typography.labelLarge)
    }
}

/** Holds the Edit pill when the Greeting widget isn't at the top. */
@Composable
fun ForYouTopRow(onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("For You", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        EditButton(onEdit)
    }
}
