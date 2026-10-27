package com.reelscore.app.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reelscore.app.distance.DistanceFormat
import com.reelscore.app.journey.City
import com.reelscore.app.journey.Geo
import com.reelscore.app.journey.GeoData
import com.reelscore.app.journey.JourneyStore
import com.reelscore.app.journey.Place
import com.reelscore.app.journey.distanceMeters
import com.reelscore.app.ui.components.NavRow
import com.reelscore.app.ui.components.RowGap
import com.reelscore.app.ui.components.SectionTitle
import com.reelscore.app.ui.settings.PageHeader

/** Only India asks for a state before the city; everywhere else it's country, then city. */
private const val STATE_COUNTRY = "IN"

private enum class Step { COUNTRY, STATE, CITY }

private data class Option(val key: String, val title: String, val subtitle: String? = null)

/** One end of the journey: country, then (India only) state, then city. */
private class EndPoint {
    var country by mutableStateOf<String?>(null)
    var state by mutableStateOf<String?>(null)
    var city by mutableStateOf<City?>(null)

    val needsState: Boolean get() = country == STATE_COUNTRY
    val canPickCity: Boolean get() = country != null && (!needsState || state != null)

    /** The chosen city, or (when a city isn't required) the country's capital. */
    fun point(data: GeoData, cityRequired: Boolean): City? = city ?: country?.takeIf { !cityRequired }?.let(data::countryPoint)

    fun name(data: GeoData): String = city?.name ?: country?.let(data::countryName).orEmpty()

    /** A city stays a city; a country-only end point is stored under the country's name. */
    fun toPlace(data: GeoData, at: City): Place {
        val chosen = city
        return if (chosen != null) {
            Place(chosen.name, data.regionName(chosen.iso, chosen.regionCode), data.countryName(chosen.iso), chosen.lat, chosen.lon)
        } else {
            val countryName = data.countryName(country!!)
            Place(countryName, null, countryName, at.lat, at.lon)
        }
    }
}

/** Says which capitals a country-only journey is measured from. */
private fun capitalNote(fromIsCountry: Boolean, toIsCountry: Boolean, a: City, b: City): String? = when {
    fromIsCountry && toIsCountry -> "Measured capital to capital: ${a.name} to ${b.name}"
    fromIsCountry -> "Measured from the capital, ${a.name}"
    toIsCountry -> "Measured to the capital, ${b.name}"
    else -> null
}

/**
 * From and To, each a country with its city underneath. Different countries can start with just
 * the countries; the same country needs both cities. Distance is a straight line at real scale.
 */
@Composable
fun JourneyScreen(onBack: () -> Unit, onStarted: () -> Unit) {
    val context = LocalContext.current
    val geo by produceState<GeoData?>(null) { value = Geo.load(context) }
    val from = remember { EndPoint() }
    val to = remember { EndPoint() }
    var picking by remember { mutableStateOf<Pair<EndPoint, Step>?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        PageHeader("Start a journey", onBack)
        val data = geo
        if (data == null) {
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        // Different countries: state and city are optional (a country is measured from its capital).
        // Same country: both cities are required, since the country alone gives no distance.
        val sameCountry = from.country != null && from.country == to.country
        val bothCountries = from.country != null && to.country != null
        EndPointRows("From", from, data, optional = bothCountries && !sameCountry) { step -> picking = from to step }
        EndPointRows("To", to, data, optional = bothCountries && !sameCountry) { step -> picking = to to step }

        if (bothCountries) {
            val a = from.point(data, sameCountry)
            val b = to.point(data, sameCountry)
            val ready = a != null && b != null && a != b
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${from.name(data)} → ${to.name(data)}", style = MaterialTheme.typography.titleLarge)
                    when {
                        a == null || b == null -> Text(
                            "Pick both cities for a journey within ${data.countryName(from.country!!)}.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        a == b -> Text(
                            "Pick two different places.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> {
                            Text(
                                "${DistanceFormat.format(distanceMeters(a.lat, a.lon, b.lat, b.lon))} in a straight line",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            capitalNote(from.city == null, to.city == null, a, b)?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (ready) {
                Button(
                    onClick = {
                        JourneyStore.start(context, from.toPlace(data, a!!), to.toPlace(data, b!!))
                        onStarted()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 52.dp),
                ) { Text("Start journey") }
            }
        }
    }

    val data = geo ?: return
    picking?.let { (point, step) ->
        val label = if (point === from) "From" else "To"
        when (step) {
            Step.COUNTRY -> Picker(
                title = "$label country",
                options = remember(data) { data.countries.map { Option(it.iso, it.name) } },
                onPick = { iso ->
                    if (iso != point.country) {
                        point.country = iso
                        point.state = null
                        point.city = null
                    }
                    picking = null
                },
                onDismiss = { picking = null },
            )
            Step.STATE -> Picker(
                title = "$label state",
                options = remember(point.country) { data.regions(point.country!!).map { Option(it.code, it.name) } },
                onPick = { code ->
                    if (code != point.state) {
                        point.state = code
                        point.city = null
                    }
                    picking = null
                },
                onDismiss = { picking = null },
            )
            Step.CITY -> {
                val iso = point.country!!
                val cities = remember(iso, point.state) { data.cities(iso, point.state) }
                Picker(
                    title = "$label city",
                    // Without a state step, show each city's state so same-named places can be told apart.
                    options = remember(cities) {
                        cities.mapIndexed { i, c -> Option(i.toString(), c.name, if (point.state == null) data.regionName(iso, c.regionCode) else null) }
                    },
                    onPick = { index ->
                        point.city = cities[index.toInt()]
                        picking = null
                    },
                    onDismiss = { picking = null },
                )
            }
        }
    }
}

/** Country row, then State (India only) and City as sub-rows underneath it, marked optional when they are. */
@Composable
private fun EndPointRows(title: String, point: EndPoint, data: GeoData, optional: Boolean, onPick: (Step) -> Unit) {
    SectionTitle(title)
    val rows = 1 + (if (point.needsState) 1 else 0) + (if (point.canPickCity) 1 else 0)
    val hint = if (optional) "Optional" else null
    var index = 0
    NavRow(Icons.Rounded.Public, "Country", index++, rows, value = point.country?.let(data::countryName) ?: "Choose") {
        onPick(Step.COUNTRY)
    }
    if (point.needsState) {
        RowGap()
        NavRow(
            Icons.Rounded.Map, "State", index++, rows,
            subtitle = hint,
            value = point.state?.let { data.regionName(point.country!!, it) } ?: "Choose",
        ) { onPick(Step.STATE) }
    }
    if (point.canPickCity) {
        RowGap()
        NavRow(Icons.Rounded.Place, "City", index, rows, subtitle = hint, value = point.city?.name ?: "Choose") { onPick(Step.CITY) }
    }
}

/** Full-screen searchable list. */
@Composable
private fun Picker(title: String, options: List<Option>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(options, query) {
        val q = query.trim()
        if (q.isEmpty()) options else options.filter { it.title.contains(q, ignoreCase = true) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    Modifier.padding(start = 8.dp, end = 20.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.key }) { option ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable(role = Role.Button) { onPick(option.key) }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(option.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (option.subtitle != null) {
                                Text(option.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (shown.isEmpty()) {
                        item {
                            Text(
                                "No matches",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
