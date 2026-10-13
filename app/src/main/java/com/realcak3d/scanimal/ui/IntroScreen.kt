package com.realcak3d.scanimal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realcak3d.scanimal.core.Anim
import com.realcak3d.scanimal.core.Sex
import com.realcak3d.scanimal.core.Species
import com.realcak3d.scanimal.core.Stage
import com.realcak3d.scanimal.data.GameRepository

private val nameIdeas = mapOf(
    Species.BLOB to listOf("Blip", "Pixel", "Bloop", "Zap"), Species.DINO to listOf("Rex", "Chomp", "Spike", "Tiny"),
    Species.DOG to listOf("Biscuit", "Scout", "Pepper", "Waffles"), Species.CAT to listOf("Mochi", "Whiskers", "Luna", "Byte"),
    Species.CHAMELEON to listOf("Prism", "Kiwi", "Shifty", "Mango"), Species.PLANT to listOf("Sprout", "Fern", "Basil", "Clover"),
    Species.FISH to listOf("Bubbles", "Fin", "Splash", "Nemo"), Species.BIRD to listOf("Tweety", "Pip", "Sky", "Chirp"),
    Species.HUMAN to listOf("Sam", "Alex", "Jo", "Max"), Species.ALIEN to listOf("Zorp", "Glim", "Nova", "Xeno"),
    Species.ROBOT to listOf("Bolt", "Gizmo", "Unit-7", "Sprocket"),
)

/** First-run (and new-generation) setup: about you → species → name & sex → incubation starts. */
@Composable
fun IntroScreen(repo: GameRepository, ownerDone: Boolean) {
    var step by rememberSaveable { mutableIntStateOf(if (ownerDone) 2 else 0) }
    var ownerName by rememberSaveable { mutableStateOf(repo.data.owner.name) }
    var month by rememberSaveable { mutableStateOf(repo.data.owner.birthday.take(2)) }
    var day by rememberSaveable { mutableStateOf(repo.data.owner.birthday.takeLast(2)) }
    var color by rememberSaveable { mutableStateOf(repo.data.owner.favoriteColor) }
    var food by rememberSaveable { mutableStateOf(repo.data.owner.favoriteFood) }
    var hobby by rememberSaveable { mutableStateOf(repo.data.owner.hobby) }
    var pet by rememberSaveable { mutableStateOf(repo.data.owner.pet) }
    var species by remember { mutableStateOf(Species.CHAMELEON) }
    var name by rememberSaveable { mutableStateOf("") }
    var sex by remember { mutableStateOf(Sex.MYSTERY) }
    Column(
        Modifier.fillMaxSize().background(Pal.bg).statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            for (i in (if (ownerDone) 2 else 0)..3) Box(Modifier.padding(3.dp).size(if (i == step) 22.dp else 8.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(if (i <= step) Pal.mint else Pal.line))
        }
        Spacer(Modifier.height(16.dp))
        when (step) {
            0 -> {
                Box(Modifier.size(220.dp)) {
                    BuddyView(BuddyLook(stage = Stage.EGG, eggProgress = 0.3f, seed = 42), Anim.NONE, 0L, Modifier.fillMaxSize())
                }
                Text("Welcome to Scanimal", color = Pal.text, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "A pocket buddy that eats barcodes, sniffs Wi-Fi and Bluetooth, stargazes at GPS satellites, goes on walks with you and makes friends with other Scanimals.\n\nFirst, let it get to know you a little.",
                    color = Pal.sub, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp,
                )
                Spacer(Modifier.height(20.dp))
                BigButton("Let's go!", Pal.mint, Modifier.fillMaxWidth()) { step = 1 }
            }
            1 -> {
                Text("About you", color = Pal.text, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("Your buddy remembers this and brings it up in conversation. Everything stays on your phone. Skip anything you like.", color = Pal.sub, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Field("Your name", ownerName) { ownerName = it.take(24) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Birthday month (1-12)", month, Modifier.weight(1f), numeric = true) { month = it.filter(Char::isDigit).take(2) }
                    Field("Day (1-31)", day, Modifier.weight(1f), numeric = true) { day = it.filter(Char::isDigit).take(2) }
                }
                Text("Favorite color", color = Pal.sub, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                ChoiceRow(listOf("Red", "Orange", "Yellow", "Green", "Blue", "Purple", "Pink", "Black", "White"), color) { color = it }
                Field("Favorite food", food) { food = it.take(30) }
                Field("Favorite hobby", hobby) { hobby = it.take(30) }
                Field("Pet's name (if you have one)", pet) { pet = it.take(20) }
                Spacer(Modifier.height(14.dp))
                BigButton("Next: choose your buddy", Pal.mint, Modifier.fillMaxWidth()) {
                    val m = month.toIntOrNull()?.takeIf { it in 1..12 }; val d = day.toIntOrNull()?.takeIf { it in 1..31 }
                    repo.act { _ ->
                        this.g.owner.apply {
                            this.name = ownerName.trim(); birthday = if (m != null && d != null) "%02d-%02d".format(m, d) else ""
                            favoriteColor = color; favoriteFood = food.trim(); this.hobby = hobby.trim(); this.pet = pet.trim(); done = true
                        }
                        if (ownerName.isNotBlank()) { this.g.ownerName = ownerName.trim(); this.g.memory["owner_name"] = ownerName.trim() }
                        if (color.isNotBlank()) this.g.memory["fav_color"] = color.lowercase()
                        if (food.isNotBlank()) { this.g.memory["fav_food"] = food.trim(); if (food.trim() !in this.g.likes) this.g.likes.add(0, food.trim()) }
                        if (hobby.isNotBlank() && hobby.trim() !in this.g.likes) this.g.likes.add(0, hobby.trim())
                        emptyList()
                    }
                    step = 2
                }
            }
            2 -> {
                Text("Choose a species", color = Pal.text, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("Every egg also rolls a random pattern and personality.", color = Pal.sub, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                listOf(Species.CHAMELEON).chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { sp ->
                            val sel = sp == species
                            Column(
                                Modifier.weight(1f).padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp))
                                    .background(if (sel) Pal.mint.copy(alpha = 0.18f) else Pal.panel)
                                    .border(if (sel) 2.dp else 1.dp, if (sel) Pal.mint else Pal.line, RoundedCornerShape(16.dp))
                                    .clickable { species = sp }.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                    BuddyView(BuddyLook(hue = sp.defaultHue, stage = Stage.ADULT, species = sp, seed = sp.ordinal * 977L, happiness = 80.0, sex = sex),
                                        if (sel) Anim.HAPPY else Anim.NONE, if (sel) System.currentTimeMillis() else 0L, Modifier.fillMaxSize())
                                }
                                Text("${sp.emoji} ${sp.label}", color = Pal.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Text(species.blurb, color = Pal.amber, fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp))
                BigButton("Next: name it", Pal.mint, Modifier.fillMaxWidth()) { step = 3 }
            }
            else -> {
                Box(Modifier.size(200.dp)) {
                    BuddyView(BuddyLook(hue = species.defaultHue, stage = Stage.BABY, species = species, sex = sex, seed = 7L, happiness = 90.0), Anim.NONE, 0L, Modifier.fillMaxSize())
                }
                Text("Name your ${species.label.lowercase()}", color = Pal.text, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Field("Name", name) { name = it.take(16) }
                ChoiceRow(nameIdeas[species].orEmpty(), name) { name = it }
                Text("Sex", color = Pal.sub, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                ChoiceRow(Sex.entries.map { "${it.symbol} ${it.label}" } + "🎲 Random", "${sex.symbol} ${sex.label}") { label ->
                    sex = Sex.entries.firstOrNull { "${it.symbol} ${it.label}" == label } ?: Sex.entries.random()
                }
                Spacer(Modifier.height(14.dp))
                Text("The egg takes about 15 minutes to hatch. Tap it and scan things to keep it warm and speed it up.", color = Pal.sub, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Back", Pal.line, Modifier.weight(0.4f)) { step = 2 }
                    BigButton("🥚  Start incubating", Pal.amber, Modifier.weight(1f)) {
                        val n = name.trim().ifBlank { nameIdeas[species]!!.random() }
                        repo.act { now -> layEgg(n, sex, species, now) }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), numeric: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        modifier = modifier.padding(vertical = 4.dp),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (numeric) androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Pal.mint, unfocusedBorderColor = Pal.line, focusedTextColor = Pal.text, unfocusedTextColor = Pal.text),
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChoiceRow(options: List<String>, selected: String, onPick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { o ->
            val sel = o == selected
            Text(o, color = if (sel) Pal.bg else Pal.text, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) Pal.mint else Pal.panel2).clickable { onPick(o) }.padding(horizontal = 12.dp, vertical = 7.dp))
        }
    }
}
