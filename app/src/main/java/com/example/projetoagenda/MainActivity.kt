package com.example.projetoagenda

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import coil.compose.AsyncImage
import com.example.projetoagenda.ui.theme.ProjetoAgendaTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@Serializable
sealed class ContentBlock {
    @Serializable
    data class TextBlock(val text: String) : ContentBlock()

    @Serializable
    data class ImageBlock(val uri: String) : ContentBlock()
}

@Serializable
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: List<ContentBlock>,
    val date: String
)

class NoteRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)
    private val notesKey = "notes_list"

    private val json = Json { ignoreUnknownKeys = true }
    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun getNotes(): List<Note> {

        val notesJson = prefs.getString(notesKey, null)
        if (notesJson != null) {
            try {
                return json.decodeFromString<List<Note>>(notesJson).sortedBy { it.title }
            } catch (e: Exception) {

            }
        }

        try {
            val oldNotesSet = prefs.getStringSet(notesKey, null)
            if (oldNotesSet != null) {
                val currentDate = sdf.format(Date())
                val migratedNotes = oldNotesSet.map { noteString ->
                    val parts = if (noteString.contains(";;;")) noteString.split(";;;", limit = 3) else null
                    Note(
                        id = parts?.get(0) ?: UUID.randomUUID().toString(),
                        title = parts?.get(1) ?: noteString,
                        content = listOf(ContentBlock.TextBlock(parts?.get(2) ?: noteString)),
                        date = currentDate
                    )
                }
                saveNotes(migratedNotes)
                return migratedNotes.sortedBy { it.title }
            }
        } catch (e: ClassCastException) {

        }

        prefs.edit { clear() }
        return emptyList()
    }

    fun saveNotes(notes: List<Note>) {
        val notesJson = json.encodeToString(notes)
        prefs.edit {
            remove(notesKey)
            putString(notesKey, notesJson)
        }
    }
}

sealed class AppScreen {
    object NoteList : AppScreen()
    data class EditNote(val note: Note) : AppScreen()
    object Calendar : AppScreen()
}

class MainActivity : ComponentActivity() {
    private lateinit var repository: NoteRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = NoteRepository(applicationContext)
        setContent {
            ProjetoAgendaTheme {
                NotesApp(repository)
            }
        }
    }
}

@Composable
fun NotesApp(repository: NoteRepository) {
    var notes by remember { mutableStateOf(repository.getNotes()) }
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.NoteList) }

    fun updateNotes(newNotes: List<Note>) {
        notes = newNotes.sortedBy { it.title }
        repository.saveNotes(newNotes)
    }

    when (val screen = currentScreen) {
        is AppScreen.NoteList -> {
            NoteScreen(
                notes = notes,
                onAddNote = {
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).apply { timeZone = TimeZone.getTimeZone("UTC") }
                    val newNote = Note(
                        title = "New Note",
                        content = listOf(ContentBlock.TextBlock("")),
                        date = sdf.format(Date())
                    )
                    updateNotes(notes + newNote)
                    currentScreen = AppScreen.EditNote(newNote)
                },
                onNoteClick = { note ->
                    currentScreen = AppScreen.EditNote(note)
                },
                onDeleteNote = { note ->
                    updateNotes(notes - note)
                },
                onMenuClick = {
                    currentScreen = AppScreen.Calendar
                }
            )
        }

        is AppScreen.EditNote -> {
            EditNoteScreen(
                note = screen.note,
                onSave = { updatedNote ->
                    val newNotes = notes.map { if (it.id == updatedNote.id) updatedNote else it }
                    updateNotes(newNotes)
                    currentScreen = AppScreen.NoteList
                }
            )
        }

        is AppScreen.Calendar -> {
            CalendarScreen(
                onBack = {
                    currentScreen = AppScreen.NoteList
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, color = Color.White) },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF29a4e6),
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    notes: List<Note>,
    onAddNote: () -> Unit,
    onNoteClick: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onMenuClick: () -> Unit
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Notes",
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNote) {
                Text("+")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            items(items = notes, key = { note -> note.id }) { note ->
                NoteItem(
                    note = note,
                    onClick = { onNoteClick(note) },
                    onDelete = { onDeleteNote(note) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNoteScreen(note: Note, onSave: (Note) -> Unit) {
    var title by remember { mutableStateOf(note.title) }
    var content by remember { mutableStateOf(note.content) }

    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = remember(note.date) {
            try {
                sdf.parse(note.date)?.time
            } catch (e: Exception) {
                null
            }
        }
    )
    var showDatePicker by remember { mutableStateOf(false) }
    val selectedDate = datePickerState.selectedDateMillis?.let {
        sdf.format(Date(it))
    } ?: note.date

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            content = content + ContentBlock.ImageBlock(it.toString())
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Edit Note",
                navigationIcon = {
                    IconButton(onClick = { onSave(note.copy(title = title, content = content, date = selectedDate)) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = "Add Image")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            TextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = selectedDate,
                onValueChange = {},
                readOnly = true,
                label = { Text("Date") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(content.size) { index ->
                    when (val block = content[index]) {
                        is ContentBlock.TextBlock -> {
                            TextField(
                                value = block.text,
                                onValueChange = { newText ->
                                    val newContent = content.toMutableList()
                                    newContent[index] = ContentBlock.TextBlock(newText)
                                    content = newContent
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        is ContentBlock.ImageBlock -> {
                            AsyncImage(
                                model = block.uri,
                                contentDescription = "Note image",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Calendar",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val datePickerState = rememberDatePickerState()
            DatePicker(
                state = datePickerState,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun NoteItem(note: Note, onClick: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = note.title,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        onClick = {
                            onDelete()
                            showMenu = false
                        }, text = { Text("Delete") })
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NoteScreenPreview() {
    ProjetoAgendaTheme {
        NoteScreen(
            notes = listOf(Note(title = "Note 1", content = listOf(ContentBlock.TextBlock("Content 1")), date = "25/07/2024")),
            onAddNote = {},
            onNoteClick = { _ -> },
            onDeleteNote = {},
            onMenuClick = {}
        )
    }
}
