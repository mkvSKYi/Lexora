# Aurora Dark Library Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the Library into a premium "Aurora Dark" home — generated gradient cover art, a Continue-reading hero, per-book progress, refined elevated cards, aurora header, illustrated empty state, and entrance motion.

**Architecture:** A new reactive `observeBooksWithProgress` flow carries each book + its reading percent. A pure `coverPalette(seed)` + `BookCover` composable generate handsome deterministic gradient covers. `LibraryScreen` becomes a single scrolling `LazyVerticalGrid` (header + hero as full-span items, then redesigned cards). The long-press context menu, tap-to-open, and delete are preserved.

**Tech Stack:** Kotlin, Compose, Material 3, Hilt, Room, Coroutines, Coil. Test: JUnit4, Robolectric (in-memory Room), MockK, kotlinx-coroutines-test.

## Global Constraints

- minSdk 26, compileSdk/targetSdk 36. Kotlin + Compose + Material 3. Hilt DI, MVVM, unidirectional Flow. ALL versions via the catalog; no hardcoded versions in module build files.
- Aurora Dark visual language: deep near-black canvas + subtle top aurora glow; violet→cyan accent family (curated, NOT dynamic color); card radius 20dp / hero 28dp; soft shadows; staggered fade+rise entrance; card press scale ≈0.96.
- `percent` is a 0..1 fraction; show progress bars only when `percent > 0`.
- Long-press context menu (Details/Delete), tap-to-open, and complete delete MUST keep working through the restructure.
- Existing signatures: `Book(id,title,author,coverPath,filePath,addedAt,lastOpenedAt)`; `BookEntity` (same fields); `BookDao` (Room, has `observeBooks`, `deleteBook`, `getProgress`, `deleteProgress`); `LibraryRepository`/`DefaultLibraryRepository(bookDao, savedWordDao)`; `LibraryViewModel(repo, importer)` exposes `uiState: StateFlow<LibraryUiState>` (`Loading` | `Content(books)`), `deleteBook(book)`, `progressPercent(bookId)`; `LibraryScreen(onBookClick, onOpenSaved, viewModel)` with `BookCard`, `Bookshelf`, `EmptyLibrary`, and the context menu in `BookContextMenu.kt`.

---

### Task 1: Books-with-progress reactive flow

**Files:**
- Create: `core/database/src/main/kotlin/com/reader/core/database/dao/BookWithProgressRow.kt`
- Modify: `core/database/src/main/kotlin/com/reader/core/database/dao/BookDao.kt`
- Create: `core/data/src/main/kotlin/com/reader/core/data/model/BookWithProgress.kt`
- Modify: `core/data/src/main/kotlin/com/reader/core/data/mapper/` (the Book mapper file — add `toDomain` for the row)
- Modify: `core/data/src/main/kotlin/com/reader/core/data/LibraryRepository.kt`
- Modify: `feature/library/src/main/kotlin/com/reader/feature/library/LibraryUiState.kt`
- Modify: `feature/library/src/main/kotlin/com/reader/feature/library/LibraryViewModel.kt`
- Test: `core/data/src/test/kotlin/com/reader/core/data/BooksWithProgressTest.kt`

**Interfaces:**
- Consumes: `BookEntity`, `BookEntity.toDomain()`, `ReadingProgressEntity`.
- Produces:
  - `BookWithProgressRow(@Embedded book: BookEntity, percent: Double)` (Room POJO).
  - `BookDao.observeBooksWithProgress(): Flow<List<BookWithProgressRow>>`.
  - `BookWithProgress(book: Book, percent: Double)` (domain) + `BookWithProgressRow.toDomain(): BookWithProgress`.
  - `LibraryRepository.observeBooksWithProgress(): Flow<List<BookWithProgress>>`.
  - `LibraryUiState.Content(books: List<BookWithProgress>)`.

- [ ] **Step 1: Add the Room POJO + query**

`BookWithProgressRow.kt`:
```kotlin
package com.reader.core.database.dao

import androidx.room.Embedded
import com.reader.core.database.entity.BookEntity

data class BookWithProgressRow(
    @Embedded val book: BookEntity,
    val percent: Double,
)
```
In `BookDao.kt` add (import `BookWithProgressRow`):
```kotlin
    @Query(
        "SELECT b.*, COALESCE(rp.percent, 0.0) AS percent FROM books b " +
            "LEFT JOIN reading_progress rp ON rp.bookId = b.id " +
            "ORDER BY b.lastOpenedAt DESC, b.addedAt DESC",
    )
    fun observeBooksWithProgress(): kotlinx.coroutines.flow.Flow<List<BookWithProgressRow>>
```

- [ ] **Step 2: Write the failing test**

`BooksWithProgressTest.kt`:
```kotlin
package com.reader.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.reader.core.data.model.Book
import com.reader.core.database.ReaderDatabase
import com.reader.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BooksWithProgressTest {
    private lateinit var db: ReaderDatabase
    private lateinit var repo: DefaultLibraryRepository

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, ReaderDatabase::class.java).allowMainThreadQueries().build()
        repo = DefaultLibraryRepository(db.bookDao(), db.savedWordDao())
    }
    @After fun tearDown() = db.close()

    @Test fun observeBooksWithProgress_maps_percent_and_defaults_zero() = runTest {
        val a = repo.addBook(Book(0, "A", null, null, "/a.epub", addedAt = 1L, lastOpenedAt = 10L))
        val b = repo.addBook(Book(0, "B", null, null, "/b.epub", addedAt = 2L, lastOpenedAt = null))
        db.bookDao().upsertProgress(ReadingProgressEntity(a, null, 0.5, 3L))

        val rows = repo.observeBooksWithProgress().first()
        assertEquals(2, rows.size)
        assertEquals("A", rows[0].book.title)       // most-recently-opened first
        assertEquals(0.5, rows[0].percent, 0.0001)
        assertEquals("B", rows[1].book.title)
        assertEquals(0.0, rows[1].percent, 0.0001)   // no progress row → 0.0
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*BooksWithProgressTest*"`
Expected: FAIL — `observeBooksWithProgress`/`BookWithProgress` unresolved.

- [ ] **Step 4: Implement domain + mapper + repo**

`BookWithProgress.kt`:
```kotlin
package com.reader.core.data.model

data class BookWithProgress(
    val book: Book,
    val percent: Double,
)
```
In the Book mapper file (where `BookEntity.toDomain()` lives) add:
```kotlin
import com.reader.core.database.dao.BookWithProgressRow
import com.reader.core.data.model.BookWithProgress

fun BookWithProgressRow.toDomain(): BookWithProgress = BookWithProgress(book.toDomain(), percent)
```
In `LibraryRepository.kt`: add to the interface `fun observeBooksWithProgress(): Flow<List<BookWithProgress>>`; in the impl:
```kotlin
    override fun observeBooksWithProgress(): Flow<List<BookWithProgress>> =
        dao.observeBooksWithProgress().map { rows -> rows.map { it.toDomain() } }
```
(imports: `com.reader.core.data.model.BookWithProgress`, `com.reader.core.data.mapper.toDomain` for the row — same mapper package.)

- [ ] **Step 5: Switch the ViewModel state to with-progress**

In `LibraryUiState.kt` change `Content` to `data class Content(val books: List<com.reader.core.data.model.BookWithProgress>) : LibraryUiState`.
In `LibraryViewModel.kt` change the `uiState` source from `repo.observeBooks()` to `repo.observeBooksWithProgress()` and the `map` type arg to `List<BookWithProgress>` (keep `Content(it)`).

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*BooksWithProgressTest*"` then `./gradlew :core:database:assembleDebug`
Expected: PASS; KSP validates the JOIN SQL.

- [ ] **Step 7: Commit**

```bash
git add core/database core/data feature/library
git commit -m "feat: add reactive books-with-progress flow for the library"
```

---

### Task 2: Generated gradient cover art

**Files:**
- Create: `feature/library/src/main/kotlin/com/reader/feature/library/BookCover.kt`
- Test: `feature/library/src/test/kotlin/com/reader/feature/library/CoverPaletteTest.kt`

**Interfaces:**
- Consumes: `Book` (for `BookCover`).
- Produces:
  - `data class CoverGradient(val top: Long, val bottom: Long)` (ARGB longs, so the palette is plain-testable without Compose `Color`).
  - `fun coverPalette(seed: String): CoverGradient` (pure, deterministic).
  - `@Composable fun BookCover(book: Book, modifier: Modifier)` — gradient + title/author + initial watermark, or the real cover image with a scrim.

- [ ] **Step 1: Write the failing test (pure palette)**

`CoverPaletteTest.kt`:
```kotlin
package com.reader.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverPaletteTest {
    @Test fun deterministic_same_seed_same_gradient() {
        assertEquals(coverPalette("Atomic Habits"), coverPalette("Atomic Habits"))
    }

    @Test fun different_seeds_can_differ() {
        // Across a spread of seeds, at least two distinct gradients appear.
        val distinct = (0..20).map { coverPalette("book-$it") }.toSet()
        assertTrue(distinct.size > 1)
    }

    @Test fun never_throws_on_empty_or_unicode() {
        assertNotEquals(0L, coverPalette("").top)          // returns a real color
        coverPalette("Україна 📚")                          // no crash
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :feature:library:testDebugUnitTest --tests "*CoverPaletteTest*"`
Expected: FAIL — `coverPalette`/`CoverGradient` unresolved.

- [ ] **Step 3: Implement palette + BookCover**

`BookCover.kt`:
```kotlin
package com.reader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import coil3.compose.AsyncImage
import com.reader.core.data.model.Book
import java.io.File
import kotlin.math.absoluteValue

data class CoverGradient(val top: Long, val bottom: Long)

// Curated vibrant violet→cyan→magenta family. ARGB longs.
private val COVER_GRADIENTS = listOf(
    CoverGradient(0xFF6D5DF6, 0xFF8E54E9),
    CoverGradient(0xFF4776E6, 0xFF2EC6C4),
    CoverGradient(0xFFB24592, 0xFF6D5DF6),
    CoverGradient(0xFF0F2027, 0xFF2C5364),
    CoverGradient(0xFFEE5D8A, 0xFFA24BCF),
    CoverGradient(0xFF2BC0E4, 0xFF3A6073),
    CoverGradient(0xFFF7971E, 0xFFCB356B),
    CoverGradient(0xFF11998E, 0xFF38EF7D),
)

fun coverPalette(seed: String): CoverGradient {
    val index = (seed.hashCode().toLong().absoluteValue % COVER_GRADIENTS.size).toInt()
    return COVER_GRADIENTS[index]
}

@Composable
fun BookCover(book: Book, modifier: Modifier = Modifier) {
    val coverPath = book.coverPath
    Box(modifier = modifier, contentAlignment = Alignment.BottomStart) {
        if (coverPath != null) {
            AsyncImage(
                model = File(coverPath),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().scrim(),
            )
        } else {
            val g = coverPalette(book.title.ifBlank { book.id.toString() })
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(g.top), Color(g.bottom))))
                    .scrim(),
            ) {
                Text(
                    text = book.title.firstOrNull()?.uppercase() ?: "•",
                    color = Color.White.copy(alpha = 0.12f),
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = book.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                book.author?.let {
                    Text(
                        text = it,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A soft bottom-to-top dark scrim so overlaid text/progress stays legible. */
private fun Modifier.scrim(): Modifier = drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
            startY = size.height * 0.45f,
            endY = size.height,
        ),
    )
}
```

- [ ] **Step 4: Run the tests + build**

Run: `./gradlew :feature:library:testDebugUnitTest --tests "*CoverPaletteTest*"` then `./gradlew :feature:library:assembleDebug`
Expected: PASS (3 tests) + BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add feature/library
git commit -m "feat: add generated gradient book cover art"
```

---

### Task 3: Aurora screen — hero, refined cards, header, empty state, motion

**Files:**
- Create: `feature/library/src/main/kotlin/com/reader/feature/library/ContinueReadingCard.kt`
- Modify: `feature/library/src/main/kotlin/com/reader/feature/library/LibraryScreen.kt`

**Interfaces:**
- Consumes: `BookCover` (Task 2), `BookWithProgress`/`LibraryUiState.Content` (Task 1), `LibraryViewModel.deleteBook`/`progressPercent`, the existing `BookContextMenuSheet`/`BookDetailsDialog`/`DeleteBookDialog`.
- Produces: `ContinueReadingCard(item: BookWithProgress, onClick: () -> Unit)`; a redesigned `LibraryScreen` + `BookCard(item, onClick, onLongClick)`.

- [ ] **Step 1: Build the Continue-reading hero**

`ContinueReadingCard.kt`: a `Card` (28dp radius, soft elevation) with `Modifier.clickable(onClick)`; a `Row` — a small `BookCover(item.book)` thumbnail (~64×96dp, clipped 16dp) on the left; a `Column` on the right with a "CONTINUE READING" `labelSmall` (accent), the title (`titleMedium`, bold, 2 lines), the author (`bodySmall`, muted), a `LinearProgressIndicator(item.percent.toFloat())` (rounded, accent), and "${(item.percent*100).roundToInt()}%" text. Use theme tokens + the accent `Color(0xFF8E7BFF)`.

- [ ] **Step 2: Restructure LibraryScreen into the aurora layout**

Rewrite `LibraryScreen.kt`'s content so:
- The `Scaffold` container draws the **aurora glow**: a `Box(Modifier.fillMaxSize())` with a top `Brush.verticalGradient` overlay (two low-alpha accent colors → transparent) behind the content.
- The body is a single `LazyVerticalGrid(GridCells.Fixed(2))`:
  - **Header** as a full-span item (`span = { GridItemSpan(maxLineSpan) }`): large "Library" (`headlineLarge`, bold) + "${books.size} books" (`bodyMedium`, muted), with the saved-words `IconButton` (Bookmarks) aligned end.
  - **Hero** as a full-span item: if a `heroItem` exists (first `books` entry with `book.lastOpenedAt != null`), render `ContinueReadingCard(heroItem, onClick = { onBookClick(heroItem.book.id) })`.
  - The **grid** of all `books` via `items(books, key = { it.book.id })` → the redesigned `BookCard`.
- `BookCard(item, onClick, onLongClick)`: a `Card` (20dp, soft shadow) holding `BookCover(item.book)` at `aspectRatio(2f/3f)` clipped 20dp, with a bottom-pinned `LinearProgressIndicator` when `item.percent > 0`; below the card, the title (2 lines) + author. `Modifier.combinedClickable(onClick, onLongClick)` (tap opens, long-press menu — unchanged) and a press `graphicsLayer` scale ≈0.96 via `interactionSource`/`animateFloatAsState`.
- Keep the menu/dialog state + rendering (`menuBook`/`detailsBook`/`pendingDelete`) exactly as today, but typed to `BookWithProgress` → pass `it.book` into the context menu/dialogs (`viewModel.deleteBook(book.book)` etc.). `onBookLongClick = { menuBook = it.book }` (store the `Book`).
- **Entrance motion:** wrap the hero + each card content in an `AnimatedVisibility`/`animateFloatAsState` fade+offset that runs once on first composition (a `remember { mutableStateOf(false) }` flipped in `LaunchedEffect(Unit)`), staggering by item index (cap the per-index delay).
- Redesigned `EmptyLibrary`: a centered `Column` — a gradient-ringed `Box` with the `MenuBook` glyph, "Your library is empty" (`titleLarge`), a muted one-liner, and a filled `Button` "Import EPUB" that triggers the same `pickEpub.launch(...)` as the FAB. Keep the FAB.

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: On-device visual polish (S25 Ultra, serial RZCY51G1D6D; adb `~/Library/Android/sdk/platform-tools/adb`)**

Install, open the Library, and ITERATE on the look (screenshot → adjust spacing/colors/typography/scrim/elevation/animation until it genuinely looks premium):
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:installDebug
~/Library/Android/sdk/platform-tools/adb shell am start -n com.reader.app/.MainActivity
~/Library/Android/sdk/platform-tools/adb exec-out screencap -p > <scratchpad>/lib_redesign.png   # downscale: sips -Z 1000 ... before viewing
```
Verify: aurora header + count; hero shows cover+progress+%; gradient cover cards with progress bars + readable title/author; entrance motion; **tap opens the reader**; **long-press still shows Details/Delete**; the FAB/empty state look right. Capture final screenshots. Check logcat for FATAL.

- [ ] **Step 5: Commit**

```bash
git add feature/library
git commit -m "feat: aurora dark library screen with hero and gradient covers"
```

---

## Self-Review Notes

- **Spec coverage:** generated covers (Task 2 — `coverPalette`+`BookCover`); books-with-progress data (Task 1); hero (Task 3 `ContinueReadingCard`); refined cards with progress + press scale (Task 3); aurora header + glow (Task 3); empty state (Task 3); entrance motion (Task 3); long-press menu/tap/delete preserved (Task 3). Tests: palette purity (Task 2), the JOIN query (Task 1), on-device polish (Task 3).
- **Type consistency:** `BookWithProgress(book, percent)`, `BookWithProgressRow(@Embedded book, percent)`, `observeBooksWithProgress()`, `LibraryUiState.Content(List<BookWithProgress>)`, `CoverGradient(top: Long, bottom: Long)`, `coverPalette(seed): CoverGradient`, `BookCover(book, modifier)`, `ContinueReadingCard(item, onClick)`, `BookCard(item, onClick, onLongClick)` are used consistently. The context menu still operates on `Book` (pass `item.book`).
- **Risk:** the screen restructure must not regress tap/long-press/delete — Task 3's on-device step verifies all three. Visual quality is iterative — Task 3 explicitly loops on-device rather than declaring done at first compile.
