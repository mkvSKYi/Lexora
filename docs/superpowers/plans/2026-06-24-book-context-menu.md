# Library Book Context Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Long-press a book in the Library to open a context menu with Details (info dialog) and Delete book (confirm → full removal of the book's files + DB rows); a normal tap still opens the reader.

**Architecture:** A new `LibraryRepository.deleteBookCompletely(book)` orchestrates deletion across the EPUB/cover files + `saved_words` + `reading_progress` + `books`. `LibraryViewModel` exposes `deleteBook(book)` + `progressPercent(bookId)`. `LibraryScreen`'s `BookCard` gains a long-press that opens a Material 3 `ModalBottomSheet` menu; Details and Delete are dialogs.

**Tech Stack:** Kotlin, Compose, Material 3, Hilt, Room, Coroutines. Test: JUnit4, Robolectric (in-memory Room + temp files), MockK, kotlinx-coroutines-test, Turbine.

## Global Constraints

- minSdk 26, compileSdk/targetSdk 36. Kotlin + Compose + Material 3. Hilt DI, MVVM, unidirectional Flow. ALL versions via the catalog; no hardcoded versions in module build files.
- Deletion order is files → `saved_words` → `reading_progress` → `books` (books row removed LAST so a mid-way failure never orphans data). File deletes are best-effort (missing file ignored, never throws). Runs off the main thread.
- The existing tap-to-open (`onBookClick`) behavior is unchanged; long-press is additive via `Modifier.combinedClickable`.
- `Book` domain already has `addedAt: Long` — no domain/mapper change needed.
- Reuse Material 3 `ModalBottomSheet` (like `ReaderTocSheet`/`WordDictionarySheet`) + `AlertDialog`; theme tokens only (Delete in `colorScheme.error`).
- Existing signatures: `Book(id, title, author, coverPath, filePath, addedAt, lastOpenedAt)`; `BookDao.deleteBook(id)`, `BookDao.getProgress(bookId): ReadingProgressEntity?` (entity has `percent: Double`); `SavedWordDao` (no bookId delete yet); `LibraryRepository` ctor currently injects only `BookDao`.

---

### Task 1: Data layer — DAO deletes + full deletion + progress percent

**Files:**
- Modify: `core/database/src/main/kotlin/com/reader/core/database/dao/SavedWordDao.kt`
- Modify: `core/database/src/main/kotlin/com/reader/core/database/dao/BookDao.kt`
- Modify: `core/data/src/main/kotlin/com/reader/core/data/LibraryRepository.kt`
- Test: `core/data/src/test/kotlin/com/reader/core/data/DefaultLibraryRepositoryDeleteTest.kt`

**Interfaces:**
- Consumes: `BookDao` (`deleteBook(id)`, `getProgress(bookId): ReadingProgressEntity?`, `upsertBook`, `upsertProgress`), `SavedWordDao` (`upsert`), the Room `ReaderDatabase`, `Book` domain.
- Produces:
  - `SavedWordDao.deleteByBookId(bookId: Long)`, `BookDao.deleteProgress(bookId: Long)`.
  - `LibraryRepository.deleteBookCompletely(book: Book)` and `LibraryRepository.progressPercent(bookId: Long): Double`; `DefaultLibraryRepository` now `@Inject constructor(bookDao: BookDao, savedWordDao: SavedWordDao)`.

- [ ] **Step 1: Add the DAO delete queries**

In `SavedWordDao.kt` add:
```kotlin
    @Query("DELETE FROM saved_words WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: Long)
```
In `BookDao.kt` add:
```kotlin
    @Query("DELETE FROM reading_progress WHERE bookId = :bookId")
    suspend fun deleteProgress(bookId: Long)
```

- [ ] **Step 2: Write the failing repository test**

`DefaultLibraryRepositoryDeleteTest.kt`:
```kotlin
package com.reader.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.reader.core.data.model.Book
import com.reader.core.database.ReaderDatabase
import com.reader.core.database.entity.ReadingProgressEntity
import com.reader.core.database.entity.SavedWordEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DefaultLibraryRepositoryDeleteTest {
    private lateinit var db: ReaderDatabase
    private lateinit var repo: DefaultLibraryRepository

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, ReaderDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DefaultLibraryRepository(db.bookDao(), db.savedWordDao())
    }
    @After fun tearDown() = db.close()

    private fun tempFile(name: String): File =
        File.createTempFile(name, ".tmp").apply { writeText("x") }

    @Test fun deleteBookCompletely_removes_files_and_all_rows() = runTest {
        val epub = tempFile("book"); val cover = tempFile("cover")
        val id = repo.addBook(
            Book(0, "T", "A", cover.absolutePath, epub.absolutePath, addedAt = 1L, lastOpenedAt = null),
        )
        db.bookDao().upsertProgress(ReadingProgressEntity(id, "{}", 0.5, 2L))
        db.savedWordDao().upsert(SavedWordEntity(0, "w", "в", null, id, "T", 3L))
        // A second book must remain untouched.
        val otherEpub = tempFile("book2")
        val otherId = repo.addBook(Book(0, "T2", null, null, otherEpub.absolutePath, 1L, null))
        db.savedWordDao().upsert(SavedWordEntity(0, "w2", "в2", null, otherId, "T2", 3L))

        repo.deleteBookCompletely(repo.getBook(id)!!)

        assertFalse(epub.exists()); assertFalse(cover.exists())
        assertNull(repo.getBook(id))
        assertNull(db.bookDao().getProgress(id))
        assertEquals(0, db.savedWordDao().observeAll().first().count { it.bookId == id })
        // other book intact
        assertTrue(otherEpub.exists())
        assertEquals("T2", repo.getBook(otherId)?.title)
        assertEquals(1, db.savedWordDao().observeAll().first().count { it.bookId == otherId })
    }

    @Test fun progressPercent_returns_value_or_zero() = runTest {
        val id = repo.addBook(Book(0, "T", null, null, "/x.epub", 1L, null))
        assertEquals(0.0, repo.progressPercent(id), 0.0)        // no row yet
        db.bookDao().upsertProgress(ReadingProgressEntity(id, null, 0.42, 2L))
        assertEquals(0.42, repo.progressPercent(id), 0.0001)
    }
}
```
(Add `import kotlinx.coroutines.flow.first` if the IDE doesn't auto-add it.)

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*DefaultLibraryRepositoryDeleteTest*"`
Expected: FAIL — `deleteBookCompletely`/`progressPercent` unresolved, ctor mismatch.

- [ ] **Step 4: Implement the repository changes**

In `LibraryRepository.kt`: add to the interface:
```kotlin
    suspend fun deleteBookCompletely(book: Book)
    suspend fun progressPercent(bookId: Long): Double
```
Change the impl class + add the methods (keep all existing methods):
```kotlin
class DefaultLibraryRepository @Inject constructor(
    private val dao: BookDao,
    private val savedWordDao: com.reader.core.database.dao.SavedWordDao,
) : LibraryRepository {
    // ... existing methods unchanged ...

    override suspend fun deleteBookCompletely(book: Book) = withContext(Dispatchers.IO) {
        runCatching { File(book.filePath).delete() }
        book.coverPath?.let { path -> runCatching { File(path).delete() } }
        savedWordDao.deleteByBookId(book.id)
        dao.deleteProgress(book.id)
        dao.deleteBook(book.id)
    }

    override suspend fun progressPercent(bookId: Long): Double =
        dao.getProgress(bookId)?.percent ?: 0.0
}
```
Add imports: `import kotlinx.coroutines.Dispatchers`, `import kotlinx.coroutines.withContext`, `import java.io.File`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*DefaultLibraryRepositoryDeleteTest*"`
Expected: PASS (2 tests). Also run `./gradlew :core:database:assembleDebug` to confirm the new DAO queries compile (KSP validates the SQL).

- [ ] **Step 6: Commit**

```bash
git add core/database core/data
git commit -m "feat: add complete book deletion across files and tables"
```

---

### Task 2: LibraryViewModel — delete + progress

**Files:**
- Modify: `feature/library/src/main/kotlin/com/reader/feature/library/LibraryViewModel.kt`
- Test: `feature/library/src/test/kotlin/com/reader/feature/library/LibraryViewModelTest.kt`

**Interfaces:**
- Consumes: `LibraryRepository.deleteBookCompletely(book)`, `LibraryRepository.progressPercent(bookId): Double` (Task 1); `Book`.
- Produces: `LibraryViewModel.deleteBook(book: Book)` (fire-and-forget via viewModelScope) and `suspend fun progressPercent(bookId: Long): Double` (delegates to the repo, for the Details dialog).

- [ ] **Step 1: Write the failing test**

`LibraryViewModelTest.kt` (create; if one exists, add these methods):
```kotlin
package com.reader.feature.library

import com.reader.core.data.LibraryRepository
import com.reader.core.data.imports.EpubImporter
import com.reader.core.data.model.Book
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val repo = mockk<LibraryRepository>(relaxed = true)
    private val importer = mockk<EpubImporter>(relaxed = true)
    private val book = Book(7, "T", "A", null, "/x.epub", 1L, null)

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { repo.observeBooks() } returns flowOf(emptyList())
    }
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun deleteBook_delegates_to_repository() = runTest {
        val vm = LibraryViewModel(repo, importer)
        vm.deleteBook(book)
        advanceUntilIdle()
        coVerify(exactly = 1) { repo.deleteBookCompletely(book) }
    }

    @Test fun progressPercent_delegates_to_repository() = runTest {
        coEvery { repo.progressPercent(7) } returns 0.33
        val vm = LibraryViewModel(repo, importer)
        assertEquals(0.33, vm.progressPercent(7), 0.0001)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :feature:library:testDebugUnitTest --tests "*LibraryViewModelTest*"`
Expected: FAIL — `deleteBook`/`progressPercent` unresolved.

- [ ] **Step 3: Implement the ViewModel changes**

In `LibraryViewModel.kt`: store the repo (`private val repo: LibraryRepository`) and add:
```kotlin
    fun deleteBook(book: com.reader.core.data.model.Book) {
        viewModelScope.launch { repo.deleteBookCompletely(book) }
    }

    suspend fun progressPercent(bookId: Long): Double = repo.progressPercent(bookId)
```
(Change the constructor param `repo: LibraryRepository` to `private val repo: LibraryRepository`; the existing `repo.observeBooks()` in `uiState` keeps working.)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :feature:library:testDebugUnitTest --tests "*LibraryViewModelTest*"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add feature/library
git commit -m "feat: add deleteBook and progressPercent to library view model"
```

---

### Task 3: UI — long-press menu, details + delete dialogs, on-device smoke

**Files:**
- Create: `feature/library/src/main/kotlin/com/reader/feature/library/BookContextMenu.kt`
- Modify: `feature/library/src/main/kotlin/com/reader/feature/library/LibraryScreen.kt`

**Interfaces:**
- Consumes: `LibraryViewModel.deleteBook(book)`, `LibraryViewModel.progressPercent(bookId)` (Task 2); `Book`.
- Produces: `BookContextMenuSheet`, `BookDetailsDialog`, `DeleteBookDialog` composables; `BookCard` gains `onLongClick`.

- [ ] **Step 1: Create the menu + dialogs**

`BookContextMenu.kt`:
```kotlin
package com.reader.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reader.core.data.model.Book
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookContextMenuSheet(book: Book, onDetails: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            book.author?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            MenuRow(Icons.Outlined.Info, "Details", MaterialTheme.colorScheme.onSurface, onDetails)
            MenuRow(Icons.Filled.Delete, "Delete book", MaterialTheme.colorScheme.error, onDelete)
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(text = label, color = tint, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
fun BookDetailsDialog(book: Book, percent: Double, onDismiss: () -> Unit) {
    val added = DateFormat.getDateInstance().format(Date(book.addedAt))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(book.title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text("Added $added", style = MaterialTheme.typography.bodyMedium)
                Text("${(percent * 100).roundToInt()}% read", style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
}

@Composable
fun DeleteBookDialog(book: Book, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete book?") },
        text = { Text("Delete \"${book.title}\"? This also removes its reading progress and saved words.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

- [ ] **Step 2: Wire long-press + state into LibraryScreen**

In `LibraryScreen.kt`:
1. Add imports: `androidx.compose.foundation.ExperimentalFoundationApi`, `androidx.compose.foundation.combinedClickable`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.setValue`, `androidx.compose.runtime.produceState`.
2. `BookCard` gains `onLongClick: () -> Unit`; replace its `Modifier.clickable(onClick = onClick)` with:
```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(book: Book, onClick: () -> Unit, onLongClick: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        // ... unchanged body ...
```
3. `Bookshelf` gains `onBookLongClick: (Book) -> Unit` and passes it: `BookCard(book = book, onClick = { onBookClick(book.id) }, onLongClick = { onBookLongClick(book) })`.
4. In `LibraryScreen`, hold the menu/dialog state and render them:
```kotlin
    var menuBook by remember { mutableStateOf<Book?>(null) }
    var detailsBook by remember { mutableStateOf<Book?>(null) }
    var pendingDelete by remember { mutableStateOf<Book?>(null) }
```
   Pass `onBookLongClick = { menuBook = it }` into `Bookshelf`. After the `Scaffold` (still inside the composable), add:
```kotlin
    menuBook?.let { book ->
        BookContextMenuSheet(
            book = book,
            onDetails = { menuBook = null; detailsBook = book },
            onDelete = { menuBook = null; pendingDelete = book },
            onDismiss = { menuBook = null },
        )
    }
    detailsBook?.let { book ->
        val percent by produceState(initialValue = 0.0, book.id) { value = viewModel.progressPercent(book.id) }
        BookDetailsDialog(book = book, percent = percent, onDismiss = { detailsBook = null })
    }
    pendingDelete?.let { book ->
        DeleteBookDialog(
            book = book,
            onConfirm = { viewModel.deleteBook(book); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: On-device smoke (S25 Ultra, serial RZCY51G1D6D; adb at `~/Library/Android/sdk/platform-tools/adb`)**

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:installDebug
~/Library/Android/sdk/platform-tools/adb shell am start -n com.reader.app/.MainActivity
```
On the Library screen: **long-press a book** (`adb shell input swipe X Y X Y 800` on a cover) → the context menu sheet appears (title + Details + Delete). Tap **Details** → the info dialog shows title/author/Added date/% read (screenshot → `…/scratchpad/menu_details.png`). Re-open the menu, tap **Delete book** → the confirm dialog → tap **Delete** → the book disappears from the shelf (screenshot → `menu_deleted.png`). Verify its DB rows are gone: `adb shell "run-as com.reader.app sqlite3 databases/reader.db 'SELECT count(*) FROM books'"` (and `saved_words`/`reading_progress` for that id). Finally, a **normal tap** on a remaining book still opens the reader (screenshot). Check logcat for FATAL. (Import a throwaway EPUB first if only one book is present, so you don't delete your real one — or re-import after.)

- [ ] **Step 5: Commit**

```bash
git add feature/library
git commit -m "feat: long-press book for context menu with details and delete"
```

---

## Self-Review Notes

- **Spec coverage:** long-press menu (Task 3); Details dialog with title/author/added/percent (Task 3 + `progressPercent` Task 1–2); Delete with confirmation + full files+3-tables removal in order (Task 1 repo, Task 2 VM, Task 3 confirm); reactive shelf update (existing Flow); tap-to-open unchanged (Task 3 `combinedClickable`). Out-of-scope items (rename/share/finished) are excluded.
- **Type consistency:** `deleteBookCompletely(book: Book)`, `progressPercent(bookId: Long): Double`, `SavedWordDao.deleteByBookId`, `BookDao.deleteProgress`, `LibraryViewModel.deleteBook(book)` / `progressPercent`, `BookContextMenuSheet(book,onDetails,onDelete,onDismiss)`, `BookDetailsDialog(book,percent,onDismiss)`, `DeleteBookDialog(book,onConfirm,onDismiss)`, `BookCard(book,onClick,onLongClick)` are used consistently. `Book` already carries `addedAt`.
- **Risk:** the repository deletion is the load-bearing change (orphans) — covered by the Task 1 cross-table+files test asserting full cleanup AND a second book left intact. The `combinedClickable` change must preserve tap-to-open — the Task 3 smoke verifies both gestures.
