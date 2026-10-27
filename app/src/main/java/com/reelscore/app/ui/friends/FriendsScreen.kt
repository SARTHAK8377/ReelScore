package com.reelscore.app.ui.friends

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelscore.app.account.Account
import com.reelscore.app.account.AddResult
import com.reelscore.app.account.Friend
import com.reelscore.app.account.Friends
import com.reelscore.app.account.Period
import com.reelscore.app.account.Privacy
import com.reelscore.app.account.Profile
import com.reelscore.app.account.Standing
import com.reelscore.app.account.UsernameTakenException
import com.reelscore.app.data.ReelRepository
import com.reelscore.app.ui.components.Avatar
import com.reelscore.app.ui.components.Card
import com.reelscore.app.ui.components.CardGap
import com.reelscore.app.ui.components.Label
import com.reelscore.app.ui.components.PillTabs
import com.reelscore.app.ui.components.RowGap
import com.reelscore.app.ui.components.SectionHeader
import com.reelscore.app.ui.components.enter
import com.reelscore.app.ui.components.tabContentPadding
import com.reelscore.app.ui.theme.groupShape
import java.text.NumberFormat
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private sealed interface Loaded<out T> {
    data object Loading : Loaded<Nothing>
    data class Done<T>(val value: T) : Loaded<T>
}

private fun <T> Flow<T>.loaded(): Flow<Loaded<T>> = map { Loaded.Done(it) }

@Composable
fun FriendsScreen() {
    val user by remember { Account.user() }.collectAsStateWithLifecycle(Account.auth.currentUser)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(tabContentPadding()),
    ) {
        Text("Friends", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.enter(0))
        Spacer(Modifier.height(20.dp))
        val uid = user?.uid
        if (uid == null) SignInCard() else SignedIn(uid)
    }
}

@Composable
private fun SignInCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().enter(1), padding = 22.dp) {
        Row(Modifier.padding(bottom = 18.dp)) {
            listOf("R", "A", "K").forEachIndexed { i, n -> Avatar(n, 44.dp, Modifier.padding(end = 6.dp), you = i == 1) }
        }
        Text("Compare with friends", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Add friends by username and see each other's totals for today, this week and all time.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        listOf(
            "Only your username, privacy choice and three totals go online",
            "Every single reel and app stays on this phone",
            "You choose who sees your scores",
        ).forEach {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 7.dp).size(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.onSurface))
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
            }
        }
        Button(
            enabled = !busy,
            onClick = {
                busy = true
                scope.launch {
                    runCatching { Account.signIn(context) }.onFailure { e -> Account.signInFailureMessage(e)?.let { toast(context, it) } }
                    busy = false
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp).heightIn(min = 52.dp),
        ) { Text("Sign in with Google", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun SignedIn(uid: String) {
    val context = LocalContext.current
    val profile by remember(uid) { Account.profile(uid).loaded() }.collectAsStateWithLifecycle(Loaded.Loading)
    when (val state = profile) {
        Loaded.Loading -> Centered { CircularProgressIndicator() }
        is Loaded.Done -> {
            val me = state.value
            if (me == null) {
                UsernamePicker()
            } else {
                // Keeps the phone's copy right after signing in on a new phone.
                LaunchedEffect(me.username, me.privacy) { Account.remember(context, me.username, me.privacy) }
                Board(me)
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) =
    Row(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalArrangement = Arrangement.Center) { content() }

@Composable
private fun UsernamePicker() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var privacy by rememberSaveable { mutableStateOf(Privacy.FRIENDS) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val valid = Account.USERNAME.matches(name)

    Card(Modifier.fillMaxWidth().enter(1), padding = 22.dp) {
        Text("Pick a username", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Friends find you by it. 3 to 20 letters, numbers, dots or underscores. It can't be changed later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(20); error = null },
            label = { Text("Username") },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SectionHeader("Who can see your scores")
    PrivacyChoice(privacy) { privacy = it }
    Button(
        enabled = valid && !busy,
        onClick = {
            busy = true
            scope.launch {
                runCatching { Account.claimUsername(context, name, privacy) }.onFailure {
                    error = if (it is UsernameTakenException) "That username is taken" else "Couldn't save. Check your connection."
                }
                busy = false
            }
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).heightIn(min = 52.dp),
    ) { Text("Continue", style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun PrivacyChoice(selected: Privacy, onSelect: (Privacy) -> Unit) {
    Privacy.entries.forEachIndexed { index, option ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(groupShape(index, Privacy.entries.size))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(option.label, style = MaterialTheme.typography.titleMedium)
                Text(option.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = option == selected, onClick = null)
        }
        if (index < Privacy.entries.lastIndex) RowGap()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Board(me: Profile) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ReelRepository.get(context) }
    var everyone by rememberSaveable { mutableStateOf(false) }
    var period by rememberSaveable { mutableStateOf(Period.TODAY) }
    var highFirst by rememberSaveable { mutableStateOf(true) }
    var adding by remember { mutableStateOf(false) }
    var showRequests by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<Standing?>(null) }

    val friends by remember(me.uid) { Friends.friends(me.uid) }.collectAsStateWithLifecycle(emptyList())
    val incoming by remember(me.uid) { Friends.incoming(me.uid) }.collectAsStateWithLifecycle(emptyList())
    val outgoing by remember(me.uid) { Friends.outgoing(me.uid) }.collectAsStateWithLifecycle(emptyList())
    // Your own line uses the live counts on this phone, not the last upload.
    val local by remember {
        combine(
            repo.total(ReelRepository.Period.TODAY),
            repo.total(ReelRepository.Period.WEEK),
            repo.total(ReelRepository.Period.ALL_TIME),
        ) { t, w, a -> Triple(t.toLong(), w.toLong(), a.toLong()) }
    }.collectAsStateWithLifecycle(Triple(me.today, me.week, me.allTime))
    val mine = me.copy(
        today = local.first,
        week = local.second,
        allTime = local.third,
        todayDate = LocalDate.now().toString(),
        weekStart = Friends.thisWeek(),
    )

    // Friends' scores are fetched when the list changes, not on every reel you watch.
    var friendProfiles by remember { mutableStateOf<Loaded<Map<Friend, Profile?>>>(Loaded.Loading) }
    LaunchedEffect(friends) { friendProfiles = Loaded.Done(runCatching { Friends.friendProfiles(friends) }.getOrDefault(emptyMap())) }
    var everyoneRows by remember { mutableStateOf<Loaded<List<Standing>>>(Loaded.Loading) }
    LaunchedEffect(everyone, period, highFirst) {
        if (!everyone) return@LaunchedEffect
        everyoneRows = Loaded.Loading
        everyoneRows = Loaded.Done(runCatching { Friends.everyoneBoard(me.uid, period, highFirst) }.getOrDefault(emptyList()))
    }
    val rows: Loaded<List<Standing>> = if (everyone) {
        everyoneRows
    } else {
        when (val loaded = friendProfiles) {
            Loaded.Loading -> Loaded.Loading
            is Loaded.Done -> Loaded.Done(Friends.friendsBoard(mine, loaded.value, period, highFirst))
        }
    }

    Row(Modifier.enter(1), verticalAlignment = Alignment.CenterVertically) {
        PillTabs(listOf("Friends", "Everyone"), if (everyone) 1 else 0, { everyone = it == 1 }, Modifier.weight(1f))
        IconButton(onClick = { highFirst = !highFirst }) {
            Icon(
                Icons.AutoMirrored.Rounded.Sort,
                contentDescription = if (highFirst) "Showing most first. Show fewest first" else "Showing fewest first. Show most first",
            )
        }
    }
    PillTabs(Period.entries.map { it.label }, period.ordinal, { period = Period.entries[it] }, Modifier.padding(top = 8.dp).enter(1))

    when (val state = rows) {
        Loaded.Loading -> Centered { CircularProgressIndicator() }
        is Loaded.Done -> {
            val scored = state.value
            if (scored.isEmpty() || (!everyone && friends.isEmpty())) {
                Card(Modifier.fillMaxWidth().padding(top = CardGap).enter(2)) {
                    Text(
                        if (everyone) "No public scores yet" else "Add a friend to compare",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (everyone) "Public accounts show up here." else "Once they accept, you'll see each other's totals here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val withScore = scored.filter { it.score != null }
            // A podium needs at least two people; alone, you're just a row.
            if (withScore.size >= 2) {
                Podium(withScore.take(3), Modifier.padding(top = CardGap).enter(2)) { opened = it }
            }
            val rest = withScore.drop(if (withScore.size >= 2) 3 else 0) + scored.filter { it.score == null }
            if (rest.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(top = CardGap).enter(3), padding = 0.dp) {
                    rest.forEachIndexed { i, row ->
                        val place = if (row.score == null) null else withScore.indexOf(row) + 1
                        StandingRow(place, row) { opened = row }
                        if (i < rest.lastIndex) HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
    if (everyone && me.privacy != Privacy.PUBLIC) {
        Text(
            "You're not on this list because your scores aren't public. You can change that in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp),
        )
    }

    Row(Modifier.fillMaxWidth().padding(top = 16.dp).enter(4), horizontalArrangement = Arrangement.spacedBy(CardGap)) {
        Button(
            onClick = { adding = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
        ) {
            Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add friend", style = MaterialTheme.typography.titleSmall)
        }
        Button(
            onClick = { showRequests = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
        ) {
            Text(if (incoming.isEmpty()) "Requests" else "Requests · ${incoming.size}", style = MaterialTheme.typography.titleSmall)
        }
    }

    if (adding) {
        AddFriendSheet(
            onAdd = { name ->
                val result = Friends.add(me.uid, me.username, name)
                when (result) {
                    AddResult.Sent -> toast(context, "Request sent to $name")
                    AddResult.Accepted -> toast(context, "You and $name are now friends")
                    else -> {}
                }
                result
            },
            onDismiss = { adding = false },
        )
    }

    if (showRequests) {
        ModalBottomSheet(onDismissRequest = { showRequests = false }, containerColor = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
                Text("Requests", style = MaterialTheme.typography.headlineMedium)
                Label("Sent to you", Modifier.padding(top = 18.dp, bottom = 8.dp))
                if (incoming.isEmpty()) {
                    Text("Nothing waiting.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                incoming.forEachIndexed { index, request ->
                    RequestRow(request.fromName, index, incoming.size) {
                        TextButton(onClick = { scope.launch { runCatching { Friends.dismiss(request) } } }) { Text("Decline") }
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { Friends.accept(me.uid, me.username, request) }
                                    .onFailure { toast(context, "Couldn't accept. Check your connection.") }
                            }
                        }) { Text("Accept") }
                    }
                    if (index < incoming.lastIndex) RowGap()
                }
                Label("Sent by you", Modifier.padding(top = 22.dp, bottom = 8.dp))
                if (outgoing.isEmpty()) {
                    Text("None pending.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                outgoing.forEachIndexed { index, request ->
                    RequestRow(request.toName, index, outgoing.size) {
                        TextButton(onClick = { scope.launch { runCatching { Friends.dismiss(request) } } }) { Text("Cancel") }
                    }
                    if (index < outgoing.lastIndex) RowGap()
                }
            }
        }
    }

    opened?.let { row ->
        ModalBottomSheet(onDismissRequest = { opened = null }, containerColor = MaterialTheme.colorScheme.background) {
            FriendCard(
                row = row,
                profile = if (row.isYou) mine else (friendProfiles as? Loaded.Done)?.value?.entries?.firstOrNull { it.key.uid == row.uid }?.value,
                canRemove = !row.isYou && friends.any { it.uid == row.uid },
                onRemove = {
                    opened = null
                    scope.launch { runCatching { Friends.remove(me.uid, row.uid) } }
                },
            )
        }
    }
}

/** The top three, standing on blocks of different heights. First place in the middle. */
@Composable
private fun Podium(top: List<Standing>, modifier: Modifier = Modifier, onOpen: (Standing) -> Unit) {
    val order = when (top.size) {
        3 -> listOf(1, 0, 2)
        2 -> listOf(1, 0)
        else -> listOf(0)
    }
    val heights = listOf(96.dp, 72.dp, 56.dp)
    Card(modifier.fillMaxWidth(), padding = 16.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            order.forEach { place ->
                val row = top[place]
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClickLabel = "Open ${row.name}") { onOpen(row) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(row.name, if (place == 0) 52.dp else 44.dp, you = row.isYou)
                    Text(
                        if (row.isYou) "You" else row.name,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                    )
                    Block(place, row.score ?: 0, heights[place], first = place == 0)
                }
            }
        }
    }
}

@Composable
private fun Block(place: Int, score: Long, height: Dp, first: Boolean) {
    Column(
        Modifier
            .fillMaxWidth(0.82f)
            .height(height)
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
            .background(if (first) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceContainerHigh),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val ink = if (first) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface
        Text(NumberFormat.getIntegerInstance().format(score), style = MaterialTheme.typography.headlineSmall, color = ink)
        Text("${place + 1}", style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.7f))
    }
}

@Composable
private fun StandingRow(place: Int?, row: Standing, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (row.isYou) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClickLabel = "Open ${row.name}", onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            place?.toString() ?: "",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp),
        )
        Avatar(row.name, 32.dp, you = row.isYou)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(if (row.isYou) "${row.name} (you)" else row.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.gapToday) {
                Text("Counter was off for part of the day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            row.score?.let { NumberFormat.getIntegerInstance().format(it) } ?: "Private",
            style = if (row.score == null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
            color = if (row.score == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** One person's totals, in a sheet. Friends can be removed from here. */
@Composable
private fun FriendCard(row: Standing, profile: Profile?, canRemove: Boolean, onRemove: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(row.name, 64.dp, you = row.isYou)
        Text(if (row.isYou) "${row.name} (you)" else row.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 10.dp))
        if (profile == null) {
            Text(
                "Keeps scores private",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            val today = if (profile.todayDate == LocalDate.now().toString()) profile.today else 0
            val week = if (profile.weekStart == Friends.thisWeek()) profile.week else 0
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(CardGap)) {
                listOf("Today" to today, "This week" to week, "All time" to profile.allTime).forEach { (label, value) ->
                    Card(Modifier.weight(1f), padding = 14.dp) {
                        Label(label)
                        Text(NumberFormat.getIntegerInstance().format(value), style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            if (row.gapToday) {
                Text(
                    "Counter was off for part of today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        if (canRemove) {
            TextButton(onClick = { confirm = true }, modifier = Modifier.padding(top = 12.dp)) {
                Text("Remove friend", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Remove ${row.name}?") },
            text = { Text("You'll stop seeing each other's friends-only scores.") },
            confirmButton = { TextButton(onClick = { confirm = false; onRemove() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RequestRow(name: String, index: Int, count: Int, actions: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(groupShape(index, count))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name, 36.dp)
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp).weight(1f))
        actions()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFriendSheet(onAdd: suspend (String) -> AddResult, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Add a friend", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Type their ReelScore username. They'll see your request on their Friends tab.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.lowercase().trim(); error = null },
                label = { Text("Their username") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = Account.USERNAME.matches(name) && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val result = runCatching { onAdd(name) }.getOrNull()
                        busy = false
                        error = when (result) {
                            AddResult.Sent, AddResult.Accepted -> { onDismiss(); null }
                            AddResult.NotFound -> "No one has that username"
                            AddResult.Yourself -> "That's your username"
                            AddResult.AlreadyFriends -> "You're already friends"
                            null -> "Couldn't send. Check your connection."
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 52.dp),
            ) { Text("Send request", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

private fun toast(context: android.content.Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
