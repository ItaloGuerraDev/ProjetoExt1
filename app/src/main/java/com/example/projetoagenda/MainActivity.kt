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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import coil.compose.AsyncImage
import com.example.projetoagenda.ui.theme.ProjetoAgendaTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    val content: List<ContentBlock>
)

class NoteRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)
    private val notesKey = "notes_list"

    fun getNotes(): List<Note> {
        try {
            val notesJson = prefs.getString(notesKey, null)
            if (notesJson != null) {
                return Json.decodeFromString<List<Note>>(notesJson).sortedBy { it.title }
            }
        } catch (e: ClassCastException) {
            // It's likely the old String Set format.
        } catch (e: Exception) {
            // Some other JSON decoding error.
        }

        // Migration from old format
        val oldNotesSet = prefs.getStringSet(notesKey, null) ?: return emptyList()
        val migratedNotes = oldNotesSet.map { noteString ->
            val parts = if (noteString.contains(";;;")) noteString.split(";;;", limit = 3) else null
            Note(
                id = parts?.get(0) ?: UUID.randomUUID().toString(),
                title = parts?.get(1) ?: noteString,
                content = listOf(ContentBlock.TextBlock(parts?.get(2) ?: noteString))
            )
        }
        // Save in new format right away to complete migration
        saveNotes(migratedNotes)
        return migratedNotes.sortedBy { it.title }
    }

    fun saveNotes(notes: List<Note>) {
        val notesJson = Json.encodeToString(notes)
        prefs.edit {
            remove(notesKey) // Make sure to remove old format if present
            putString(notesKey, notesJson)
        }
    }
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
    var selectedNote by remember { mutableStateOf<Note?>(null) }

    fun updateNotes(newNotes: List<Note>) {
        notes = newNotes.sortedBy { it.title }
        repository.saveNotes(newNotes)
    }

    if (selectedNote == null) {
        NoteScreen(
            notes = notes,
            onAddNote = {
                val newNote = Note(title = "New Note", content = listOf(ContentBlock.TextBlock("")))
                updateNotes(notes + newNote)
                selectedNote = newNote
            },
            onNoteClick = {
                selectedNote = it
            },
            onDeleteNote = { note ->
                updateNotes(notes - note)
            }
        )
    } else {
        selectedNote?.let { note ->
            EditNoteScreen(
                note = note,
                onSave = { updatedNote ->
                    val newNotes = notes.map { if (it.id == updatedNote.id) updatedNote else it }
                    updateNotes(newNotes)
                    selectedNote = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    notes: List<Note>,
    onAddNote: () -> Unit,
    onNoteClick: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Notes") })
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

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            content = content + ContentBlock.ImageBlock(it.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Note") },
                navigationIcon = {
                    IconButton(onClick = { onSave(note.copy(title = title, content = content)) }) {
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
            notes = listOf(Note(title = "Note 1", content = listOf(ContentBlock.TextBlock("Content 1")))),
            onAddNote = {},
            onNoteClick = { _ -> },
            onDeleteNote = {}
        )
    }
}
