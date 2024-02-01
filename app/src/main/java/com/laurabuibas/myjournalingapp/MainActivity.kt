package com.laurabuibas.myjournalingapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date

const val DATABASE_NAME = "journal_db"
const val DATABASE_VERSION = 1

// database schema
object JournalEntryTable {
    const val TABLE_NAME = "journal_entries"
    const val COLUMN_ID = "id"
    const val COLUMN_TEXT = "text"
    const val COLUMN_RATING = "rating"
    const val COLUMN_CREATED_DATE = "created_date"
}

// define data class for journal entries
data class JournalEntry(
    val id: Int,
    val text: String,
    val rating: Int,
    val createdDate: String
)

// SQLiteOpenHelper to manage database creation and version management
class JournalDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = "CREATE TABLE ${JournalEntryTable.TABLE_NAME} (" +
                "${JournalEntryTable.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "${JournalEntryTable.COLUMN_TEXT} TEXT, " +
                "${JournalEntryTable.COLUMN_RATING} INTEGER, " +
                "${JournalEntryTable.COLUMN_CREATED_DATE} TEXT)"
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS ${JournalEntryTable.TABLE_NAME}")
        onCreate(db)
    }
}

// implement database operations
class JournalRepository(private val context: Context) {
    private val databaseHelper: JournalDatabaseHelper = JournalDatabaseHelper(context)
    val entries: MutableState<List<JournalEntry>> = mutableStateOf(emptyList())

    init { loadEntries() } // initialize entries when the repository is created

    private fun loadEntries() {
        val db = databaseHelper.readableDatabase
        val cursor = db.query(
            JournalEntryTable.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "${JournalEntryTable.COLUMN_CREATED_DATE} DESC"
        )
        val loadedEntries = mutableListOf<JournalEntry>()
        with(cursor) {
            while (moveToNext()) {
                val id = getInt(getColumnIndexOrThrow(JournalEntryTable.COLUMN_ID))
                val text = getString(getColumnIndexOrThrow(JournalEntryTable.COLUMN_TEXT))
                val rating = getInt(getColumnIndexOrThrow(JournalEntryTable.COLUMN_RATING))
                val createdDate = getString(getColumnIndexOrThrow(JournalEntryTable.COLUMN_CREATED_DATE))
                loadedEntries.add(JournalEntry(id, text, rating, createdDate))
            }
        }
        cursor.close()
        db.close()
        // update the MutableState with the loaded entries
        entries.value = loadedEntries
    }

    fun addEntry(entry: JournalEntry) {
        val db = databaseHelper.writableDatabase
        val values = ContentValues().apply {
            put(JournalEntryTable.COLUMN_TEXT, entry.text)
            put(JournalEntryTable.COLUMN_RATING, entry.rating)
            put(JournalEntryTable.COLUMN_CREATED_DATE, entry.createdDate)
        }
        db.insert(JournalEntryTable.TABLE_NAME, null, values)
        db.close()
        // reload entries for freshness
        loadEntries()
    }

    fun deleteEntry(entryId: Int) {
        val db = databaseHelper.writableDatabase
        db.delete(
            JournalEntryTable.TABLE_NAME,
            "${JournalEntryTable.COLUMN_ID} = ?",
            arrayOf(entryId.toString())
        )
        db.close()

        loadEntries() // after deleting, reload entries for freshness
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = JournalRepository(context = applicationContext)
        setContent {
            JournalApp(repository = repository)
        }
    }
}

@Composable
fun JournalApp(repository: JournalRepository) {
    val entries by repository.entries

    var addingEntry by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(TextFieldValue()) }
    var selectedRating by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "My Daily Entries",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 5.dp, top=10.dp)
        )
        Spacer(modifier = Modifier.height(13.dp))

        Button(onClick = { addingEntry = true }) { Text(text = "+ Add Entry") }

        if (addingEntry) {
            AddEntryPopup(
                onDismiss = { addingEntry = false },
                text = text,
                onTextChange = { text = it },
                onRatingSelected = { selectedRating = it }
            ) {
                val sdf = SimpleDateFormat("dd/M/yyyy")
                val currentDate = sdf.format(Date())

                val entry = JournalEntry(
                    id = 0,
                    text = text.text,
                    rating = selectedRating,
                    createdDate = currentDate
                )
                repository.addEntry(entry)
                addingEntry = false // dismiss the popup after saving the entry
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { entry ->
                EntryItem(
                    entry = entry,
                    onDelete = { repository.deleteEntry(entry.id) }
                )
            }
        }
    }
}


@Composable
fun AddEntryPopup(
    onDismiss: () -> Unit,
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onRatingSelected: (Int) -> Unit,
    onAddEntry: () -> Unit
) {
    Card( modifier = Modifier.padding(16.dp).fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Add Entry")
            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Write about your day") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RatingButton(
                    image = painterResource(R.drawable.horrible),
                    onClick = { onRatingSelected(1) }
                )
                Spacer(modifier = Modifier.width(16.dp))
                RatingButton(
                    image = painterResource(R.drawable.bad),
                    onClick = { onRatingSelected(2) }
                )
                Spacer(modifier = Modifier.width(16.dp))
                RatingButton(
                    image = painterResource(R.drawable.well),
                    onClick = { onRatingSelected(3) }
                )
                Spacer(modifier = Modifier.width(16.dp))
                RatingButton(
                    image = painterResource(R.drawable.verywell),
                    onClick = { onRatingSelected(4) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = onAddEntry) { Text(text = "Save") }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = onDismiss) { Text(text = "Cancel") }
            }
        }
    }
}


@Composable
fun RatingButton(
    image: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Image(
            painter = image,
            contentDescription = null,
            modifier = Modifier.size(50.dp)
        )
    }
}



@Composable
fun EntryItem(
    entry: JournalEntry,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Created: ${entry.createdDate}"
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val image = when (entry.rating) {
                    1 -> painterResource(R.drawable.horrible)
                    2 -> painterResource(R.drawable.bad)
                    3 -> painterResource(R.drawable.well)
                    4 -> painterResource(R.drawable.verywell)
                    else -> painterResource(R.drawable.default_rating)
                }

                Image(
                    painter = image,
                    contentDescription = null,
                    modifier = Modifier.size(35.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val maxLines = if (expanded) Int.MAX_VALUE else 2
                    val displayText = if (expanded) entry.text else entry.text.take(140) // Adjust 140 according to your requirement
                    Text(
                        text = displayText,
                        maxLines = maxLines,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (entry.text.length > 20 && !expanded) {
                        TextButton(onClick = { expanded = true }) {
                            Text(text = "See more")
                        }
                    } else if (expanded) {
                        TextButton(onClick = { expanded = false }) {
                            Text(text = "See less")
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Entry"
                    )
                }
            }
        }
    }
}

