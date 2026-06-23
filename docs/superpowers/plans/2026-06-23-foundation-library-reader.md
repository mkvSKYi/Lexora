# Foundation, Library & Reader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a working Android app that imports `.epub` files into a library and reads them with Readium, saving and restoring reading progress per book.

**Architecture:** Multi-module Gradle project, MVVM + repository with unidirectional Flow. Room stores the book library and reading progress. Readium's Streamer parses EPUB metadata; Readium's `EpubNavigatorFragment` renders the book, hosted inside Compose via Fragment interop. Translation, dictionary, and full theming are deliberately deferred to later plans.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore, Navigation Compose, Readium Kotlin Toolkit, Coroutines/Flow. Test: JUnit4, MockK, Turbine, Robolectric, Compose UI test.

## Global Constraints

- Platform: Android, `minSdk 26`, `targetSdk` latest stable, `compileSdk` latest stable.
- Language: Kotlin only. UI: Jetpack Compose + Material 3. No XML layouts except the unavoidable Readium fragment host.
- Architecture: MVVM + repository, unidirectional data flow with Kotlin `Flow`. DI via Hilt.
- Module boundaries are fixed: `:app`, `:core:designsystem`, `:core:database`, `:core:data`, `:feature:library`, `:feature:reader`. (Translation/vocabulary modules arrive in later plans.)
- All dependency versions come from a Gradle **version catalog** (`gradle/libs.versions.toml`). No hardcoded versions in module `build.gradle.kts`.
- Verify exact Readium and AndroidX artifact versions/APIs against current docs at implementation time (use context7: `org.readium.kotlin-toolkit`, `androidx.room`, `androidx.hilt`). Do not invent version numbers — the version catalog is the single place to pin them.
- Reading progress is stored as a Readium `Locator` serialized to JSON.

---

### Task 0: Project scaffold and build configuration

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts` (root), `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/reader/app/ReaderApplication.kt`
- Create: `.gitignore`

**Interfaces:**
- Produces: an installable empty `:app` module with Hilt enabled (`ReaderApplication` annotated `@HiltAndroidApp`) and a single empty `MainActivity`.

- [ ] **Step 1: Create the version catalog**

In `gradle/libs.versions.toml`, declare versions and library/plugin aliases for: Android Gradle Plugin, Kotlin, KSP, Compose BOM, Compose compiler, Material3, Activity-Compose, Lifecycle/ViewModel-Compose, Navigation-Compose, Hilt + hilt-navigation-compose, Room (runtime/ktx/compiler), DataStore-preferences, Coroutines, Readium (`org.readium.kotlin-toolkit:readium-shared`, `readium-streamer`, `readium-navigator`), and test libs (junit4, mockk, turbine, robolectric, kotlinx-coroutines-test, androidx-test, compose-ui-test-junit4). Look up current stable versions via context7 before pinning.

- [ ] **Step 2: Create root build and settings**

`settings.gradle.kts` declares `pluginManagement`, `dependencyResolutionManagement` (with `mavenCentral()` and `google()`, plus the Maven repo Readium requires — verify via docs), and includes modules: `:app`, `:core:designsystem`, `:core:database`, `:core:data`, `:feature:library`, `:feature:reader`. Root `build.gradle.kts` declares all plugins `apply false`.

- [ ] **Step 3: Create `:app` module build file**

`app/build.gradle.kts` applies android-application + kotlin + ksp + hilt + compose plugins, sets `namespace = "com.reader.app"`, `minSdk 26`, compile/target to latest, enables `buildFeatures { compose = true }`, and depends on every other module plus Hilt, Compose BOM, Activity-Compose, Navigation-Compose.

- [ ] **Step 4: Create the Application and Manifest**

`ReaderApplication.kt`:

```kotlin
package com.reader.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ReaderApplication : Application()
```

`AndroidManifest.xml` registers `ReaderApplication` as `android:name`, declares `MainActivity` as launcher with a Material3 theme, and adds `READ_EXTERNAL_STORAGE` is NOT used — file import uses the Storage Access Framework (no storage permission needed).

- [ ] **Step 5: Create an empty MainActivity**

`app/src/main/kotlin/com/reader/app/MainActivity.kt` — a `@AndroidEntryPoint ComponentActivity` whose `setContent {}` renders an empty `Surface`. (Navigation is wired in Task 7.)

- [ ] **Step 6: Build to verify the project assembles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "chore: scaffold multi-module android project with hilt and compose"
```

---

### Task 1: Design system module

**Files:**
- Create: `core/designsystem/build.gradle.kts`
- Create: `core/designsystem/src/main/kotlin/com/reader/core/designsystem/theme/Theme.kt`
- Create: `core/designsystem/src/main/kotlin/com/reader/core/designsystem/theme/Color.kt`
- Create: `core/designsystem/src/main/kotlin/com/reader/core/designsystem/theme/Type.kt`

**Interfaces:**
- Produces: `@Composable fun ReaderTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)` applying a Material 3 color scheme and typography.

- [ ] **Step 1: Create the module build file**

`core/designsystem/build.gradle.kts` applies android-library + kotlin + compose, `namespace = "com.reader.core.designsystem"`, depends on Compose BOM + Material3.

- [ ] **Step 2: Define colors and typography**

`Color.kt` defines a light and a dark `ColorScheme` (basic for now; the full reading-theme set is a later plan). `Type.kt` defines a Material3 `Typography` instance.

- [ ] **Step 3: Define the theme composable**

```kotlin
package com.reader.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun ReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ReaderTypography,
        content = content,
    )
}
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew :core:designsystem:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core/designsystem
git commit -m "feat: add design system module with material3 theme"
```

---

### Task 2: Database module — entities and DAOs

**Files:**
- Create: `core/database/build.gradle.kts`
- Create: `core/database/src/main/kotlin/com/reader/core/database/entity/BookEntity.kt`
- Create: `core/database/src/main/kotlin/com/reader/core/database/entity/ReadingProgressEntity.kt`
- Create: `core/database/src/main/kotlin/com/reader/core/database/dao/BookDao.kt`
- Create: `core/database/src/main/kotlin/com/reader/core/database/ReaderDatabase.kt`
- Create: `core/database/src/main/kotlin/com/reader/core/database/di/DatabaseModule.kt`
- Test: `core/database/src/test/kotlin/com/reader/core/database/BookDaoTest.kt`

**Interfaces:**
- Produces:
  - `BookEntity(id: Long, title: String, author: String?, coverPath: String?, filePath: String, addedAt: Long, lastOpenedAt: Long?)`
  - `ReadingProgressEntity(bookId: Long, locatorJson: String?, percent: Double, updatedAt: Long)`
  - `BookDao`: `fun observeBooks(): Flow<List<BookEntity>>`, `suspend fun upsertBook(book: BookEntity): Long`, `suspend fun getBook(id: Long): BookEntity?`, `suspend fun updateLastOpened(id: Long, ts: Long)`, `suspend fun upsertProgress(p: ReadingProgressEntity)`, `suspend fun getProgress(bookId: Long): ReadingProgressEntity?`, `suspend fun deleteBook(id: Long)`.

- [ ] **Step 1: Create the module build file**

`core/database/build.gradle.kts` applies android-library + kotlin + ksp + hilt, `namespace = "com.reader.core.database"`, depends on Room (runtime/ktx + compiler via ksp), Hilt, Coroutines. Test deps: junit4, robolectric, kotlinx-coroutines-test, androidx-test-core, room-testing. Set `testOptions { unitTests.isIncludeAndroidResources = true }`.

- [ ] **Step 2: Write the failing DAO test**

```kotlin
package com.reader.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.reader.core.database.entity.BookEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookDaoTest {
    private lateinit var db: ReaderDatabase
    private lateinit var dao: BookDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), ReaderDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.bookDao()
    }

    @After fun teardown() = db.close()

    @Test fun upsert_then_observe_returns_book() = runTest {
        val id = dao.upsertBook(
            BookEntity(0, "Dune", "Herbert", null, "/books/dune.epub", 100L, null),
        )
        dao.observeBooks().test {
            val books = awaitItem()
            assertEquals(1, books.size)
            assertEquals("Dune", books.first().title)
            assertEquals(id, books.first().id)
        }
    }

    @Test fun progress_roundtrips() = runTest {
        val id = dao.upsertBook(BookEntity(0, "Dune", null, null, "/d.epub", 1L, null))
        dao.upsertProgress(ReadingProgressEntity(id, "{\"href\":\"ch1\"}", 0.42, 5L))
        val p = dao.getProgress(id)
        assertEquals(0.42, p!!.percent, 0.0001)
        assertEquals("{\"href\":\"ch1\"}", p.locatorJson)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :core:database:testDebugUnitTest`
Expected: FAIL — `ReaderDatabase`, `BookEntity`, `ReadingProgressEntity`, `BookDao` unresolved.

- [ ] **Step 4: Implement entities**

`BookEntity.kt`:

```kotlin
package com.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val filePath: String,
    val addedAt: Long,
    val lastOpenedAt: Long?,
)
```

`ReadingProgressEntity.kt`:

```kotlin
package com.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val bookId: Long,
    val locatorJson: String?,
    val percent: Double,
    val updatedAt: Long,
)
```

- [ ] **Step 5: Implement the DAO**

```kotlin
package com.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.reader.core.database.entity.BookEntity
import com.reader.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC, addedAt DESC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBook(book: BookEntity): Long

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: Long): BookEntity?

    @Query("UPDATE books SET lastOpenedAt = :ts WHERE id = :id")
    suspend fun updateLastOpened(id: Long, ts: Long)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: Long)

    @Upsert
    suspend fun upsertProgress(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun getProgress(bookId: Long): ReadingProgressEntity?
}
```

- [ ] **Step 6: Implement the database and Hilt module**

`ReaderDatabase.kt`:

```kotlin
package com.reader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.reader.core.database.dao.BookDao
import com.reader.core.database.entity.BookEntity
import com.reader.core.database.entity.ReadingProgressEntity

@Database(
    entities = [BookEntity::class, ReadingProgressEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}
```

`DatabaseModule.kt` provides a singleton `ReaderDatabase` built with `Room.databaseBuilder(context, ReaderDatabase::class.java, "reader.db")` and provides `BookDao` from it. Annotate `@Module @InstallIn(SingletonComponent::class)`.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :core:database:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add core/database
git commit -m "feat: add room database with book and reading-progress daos"
```

---

### Task 3: Data module — domain models and LibraryRepository

**Files:**
- Create: `core/data/build.gradle.kts`
- Create: `core/data/src/main/kotlin/com/reader/core/data/model/Book.kt`
- Create: `core/data/src/main/kotlin/com/reader/core/data/model/ReadingProgress.kt`
- Create: `core/data/src/main/kotlin/com/reader/core/data/mapper/BookMapper.kt`
- Create: `core/data/src/main/kotlin/com/reader/core/data/LibraryRepository.kt`
- Create: `core/data/src/main/kotlin/com/reader/core/data/di/DataModule.kt`
- Test: `core/data/src/test/kotlin/com/reader/core/data/LibraryRepositoryTest.kt`

**Interfaces:**
- Consumes: `BookDao`, `BookEntity`, `ReadingProgressEntity` from Task 2.
- Produces:
  - `data class Book(id, title, author, coverPath, filePath, addedAt, lastOpenedAt)` (domain).
  - `data class ReadingProgress(bookId, locatorJson, percent, updatedAt)`.
  - `interface LibraryRepository`: `fun observeBooks(): Flow<List<Book>>`, `suspend fun addBook(book: Book): Long`, `suspend fun getBook(id: Long): Book?`, `suspend fun markOpened(id: Long, now: Long)`, `suspend fun saveProgress(progress: ReadingProgress)`, `suspend fun getProgress(bookId: Long): ReadingProgress?`, `suspend fun deleteBook(id: Long)`.
  - `class DefaultLibraryRepository @Inject constructor(dao: BookDao)` bound in `DataModule`.

- [ ] **Step 1: Create the module build file**

`core/data/build.gradle.kts` applies android-library + kotlin + ksp + hilt, `namespace = "com.reader.core.data"`, depends on `:core:database`, Hilt, Coroutines, Readium (`readium-shared` and `readium-streamer` — used by Task 4). Test deps: junit4, mockk, turbine, kotlinx-coroutines-test.

- [ ] **Step 2: Write the failing repository test**

```kotlin
package com.reader.core.data

import app.cash.turbine.test
import com.reader.core.data.model.Book
import com.reader.core.database.dao.BookDao
import com.reader.core.database.entity.BookEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryRepositoryTest {
    private val dao = mockk<BookDao>(relaxed = true)
    private val repo = DefaultLibraryRepository(dao)

    @Test fun observeBooks_maps_entities_to_domain() = runTest {
        coEvery { dao.observeBooks() } returns flowOf(
            listOf(BookEntity(7, "Dune", "Herbert", null, "/d.epub", 1L, null)),
        )
        repo.observeBooks().test {
            val books = awaitItem()
            assertEquals(1, books.size)
            assertEquals(7L, books.first().id)
            assertEquals("Dune", books.first().title)
        }
    }

    @Test fun addBook_delegates_to_dao() = runTest {
        coEvery { dao.upsertBook(any()) } returns 9L
        val id = repo.addBook(Book(0, "Dune", null, null, "/d.epub", 1L, null))
        assertEquals(9L, id)
        coVerify { dao.upsertBook(any()) }
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: FAIL — unresolved `Book`, `DefaultLibraryRepository`.

- [ ] **Step 4: Implement domain models**

`Book.kt`:

```kotlin
package com.reader.core.data.model

data class Book(
    val id: Long,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val filePath: String,
    val addedAt: Long,
    val lastOpenedAt: Long?,
)
```

`ReadingProgress.kt`:

```kotlin
package com.reader.core.data.model

data class ReadingProgress(
    val bookId: Long,
    val locatorJson: String?,
    val percent: Double,
    val updatedAt: Long,
)
```

- [ ] **Step 5: Implement mappers**

`BookMapper.kt` provides `BookEntity.toDomain(): Book`, `Book.toEntity(): BookEntity`, `ReadingProgressEntity.toDomain()`, `ReadingProgress.toEntity()` as extension functions (field-for-field copies).

- [ ] **Step 6: Implement the repository**

```kotlin
package com.reader.core.data

import com.reader.core.data.mapper.toDomain
import com.reader.core.data.mapper.toEntity
import com.reader.core.data.model.Book
import com.reader.core.data.model.ReadingProgress
import com.reader.core.database.dao.BookDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface LibraryRepository {
    fun observeBooks(): Flow<List<Book>>
    suspend fun addBook(book: Book): Long
    suspend fun getBook(id: Long): Book?
    suspend fun markOpened(id: Long, now: Long)
    suspend fun saveProgress(progress: ReadingProgress)
    suspend fun getProgress(bookId: Long): ReadingProgress?
    suspend fun deleteBook(id: Long)
}

class DefaultLibraryRepository @Inject constructor(
    private val dao: BookDao,
) : LibraryRepository {
    override fun observeBooks(): Flow<List<Book>> =
        dao.observeBooks().map { list -> list.map { it.toDomain() } }

    override suspend fun addBook(book: Book): Long = dao.upsertBook(book.toEntity())
    override suspend fun getBook(id: Long): Book? = dao.getBook(id)?.toDomain()
    override suspend fun markOpened(id: Long, now: Long) = dao.updateLastOpened(id, now)
    override suspend fun saveProgress(progress: ReadingProgress) =
        dao.upsertProgress(progress.toEntity())
    override suspend fun getProgress(bookId: Long): ReadingProgress? =
        dao.getProgress(bookId)?.toDomain()
    override suspend fun deleteBook(id: Long) = dao.deleteBook(id)
}
```

`DataModule.kt` `@Binds` `DefaultLibraryRepository` to `LibraryRepository` in `SingletonComponent`.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add core/data
git commit -m "feat: add library repository with domain mapping"
```

---

### Task 4: EPUB import — metadata extraction and file copy

**Files:**
- Create: `core/data/src/main/kotlin/com/reader/core/data/import/ImportedBook.kt`
- Create: `core/data/src/main/kotlin/com/reader/core/data/import/EpubImporter.kt`
- Test: `core/data/src/test/kotlin/com/reader/core/data/import/EpubImporterTest.kt`
- Test asset: `core/data/src/test/resources/sample.epub`

**Interfaces:**
- Consumes: `LibraryRepository` from Task 3, Readium `Streamer`/`AssetRetriever` for parsing.
- Produces:
  - `data class ImportedBook(title: String, author: String?, coverPath: String?, filePath: String)`.
  - `class EpubImporter @Inject constructor(...)`: `suspend fun import(uri: Uri): Result<Long>` — copies the EPUB into app storage, extracts metadata + cover via Readium, persists via `LibraryRepository.addBook`, returns the new book id.

- [ ] **Step 1: Add a small valid EPUB test fixture**

Place a minimal valid `sample.epub` (title "Sample Book", author "Test Author") at `core/data/src/test/resources/sample.epub`. (Generate one with any EPUB tool, or download a public-domain title and trim.)

- [ ] **Step 2: Write the failing importer test**

The importer's metadata-parsing core is pure enough to test on the JVM via Readium. Test that parsing the fixture yields the expected title/author.

```kotlin
package com.reader.core.data.import

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class EpubImporterTest {
    @Test fun parses_title_and_author_from_epub() = runTest {
        val file = File("src/test/resources/sample.epub")
        val meta = EpubMetadataParser().parse(file)
        assertEquals("Sample Book", meta.title)
        assertEquals("Test Author", meta.author)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*EpubImporterTest*"`
Expected: FAIL — `EpubMetadataParser` unresolved.

- [ ] **Step 4: Implement the metadata parser**

Create `EpubMetadataParser` that opens a `File` with Readium's `AssetRetriever` + `PublicationOpener`/`Streamer` (verify the exact current API via context7) and reads `publication.metadata.title` and the first author from `publication.metadata.authors`. Return a small data holder `ParsedMetadata(title: String, author: String?)`. Keep this class free of Android `Context` so it stays JVM-testable; if Readium requires a `Context`, inject it and run this test with Robolectric instead.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*EpubImporterTest*"`
Expected: PASS.

- [ ] **Step 6: Implement the full importer**

`EpubImporter.import(uri)`:
1. Open an `InputStream` from the `ContentResolver` for `uri`.
2. Copy bytes to `context.filesDir/books/<uuid>.epub`.
3. Run `EpubMetadataParser` on the copied file; extract cover image to `filesDir/covers/<uuid>.png` if present (`publication.cover()`), else null.
4. Build a `Book(id=0, title, author, coverPath, filePath, addedAt=System.currentTimeMillis(), lastOpenedAt=null)` and call `libraryRepository.addBook`.
5. Wrap in `runCatching { ... }` returning `Result<Long>`; on failure delete any partial files.

- [ ] **Step 7: Build to verify it compiles**

Run: `./gradlew :core:data:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add core/data
git commit -m "feat: import epub files and extract metadata via readium"
```

---

### Task 5: Library feature — ViewModel and bookshelf UI

**Files:**
- Create: `feature/library/build.gradle.kts`
- Create: `feature/library/src/main/kotlin/com/reader/feature/library/LibraryUiState.kt`
- Create: `feature/library/src/main/kotlin/com/reader/feature/library/LibraryViewModel.kt`
- Create: `feature/library/src/main/kotlin/com/reader/feature/library/LibraryScreen.kt`
- Test: `feature/library/src/test/kotlin/com/reader/feature/library/LibraryViewModelTest.kt`

**Interfaces:**
- Consumes: `LibraryRepository` (Task 3), `EpubImporter` (Task 4).
- Produces:
  - `sealed interface LibraryUiState { object Loading; data class Content(val books: List<Book>); }`.
  - `LibraryViewModel`: `val uiState: StateFlow<LibraryUiState>`, `fun importBook(uri: Uri)`, exposes a one-shot event channel for import errors.
  - `@Composable fun LibraryScreen(onBookClick: (Long) -> Unit, viewModel: LibraryViewModel = hiltViewModel())`.

- [ ] **Step 1: Create the module build file**

`feature/library/build.gradle.kts` applies android-library + kotlin + ksp + hilt + compose, `namespace = "com.reader.feature.library"`, depends on `:core:data`, `:core:designsystem`, Compose BOM, Lifecycle-ViewModel-Compose, Hilt-Navigation-Compose. Test deps: junit4, mockk, turbine, kotlinx-coroutines-test.

- [ ] **Step 2: Write the failing ViewModel test**

```kotlin
package com.reader.feature.library

import app.cash.turbine.test
import com.reader.core.data.LibraryRepository
import com.reader.core.data.import.EpubImporter
import com.reader.core.data.model.Book
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LibraryViewModelTest {
    private val repo = mockk<LibraryRepository>(relaxed = true)
    private val importer = mockk<EpubImporter>(relaxed = true)

    @Before fun setup() = Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher())

    @Test fun emits_content_from_repository() = runTest {
        every { repo.observeBooks() } returns flowOf(
            listOf(Book(1, "Dune", "Herbert", null, "/d.epub", 1L, null)),
        )
        val vm = LibraryViewModel(repo, importer)
        vm.uiState.test {
            assertEquals(LibraryUiState.Loading, awaitItem())
            val content = awaitItem()
            assertTrue(content is LibraryUiState.Content)
            assertEquals(1, (content as LibraryUiState.Content).books.size)
        }
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :feature:library:testDebugUnitTest`
Expected: FAIL — unresolved `LibraryViewModel`, `LibraryUiState`.

- [ ] **Step 4: Implement the UI state and ViewModel**

`LibraryUiState.kt` defines the sealed interface above. `LibraryViewModel` (annotated `@HiltViewModel`, `@Inject constructor(repo, importer)`) maps `repo.observeBooks()` to `LibraryUiState.Content`, `stateIn(viewModelScope, WhileSubscribed(5000), Loading)`. `importBook(uri)` launches `importer.import(uri)` in `viewModelScope` and emits errors to a `Channel`.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :feature:library:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Implement the bookshelf Compose UI**

`LibraryScreen.kt`: a `Scaffold` with a top app bar and a FloatingActionButton that launches `rememberLauncherForActivityResult(OpenDocument())` with MIME `application/epub+zip`, calling `viewModel.importBook(uri)`. Body renders a `LazyVerticalGrid` of book cards (cover image via Coil or a placeholder, title, author). Empty state shows a "Import your first EPUB" message. Tapping a card calls `onBookClick(book.id)`. Collect the error channel and show a `Snackbar`.

- [ ] **Step 7: Build to verify it compiles**

Run: `./gradlew :feature:library:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add feature/library
git commit -m "feat: add library screen with epub import and bookshelf grid"
```

---

### Task 6: Reader feature — Readium navigator and progress persistence

**Files:**
- Create: `feature/reader/build.gradle.kts`
- Create: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderUiState.kt`
- Create: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderViewModel.kt`
- Create: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderScreen.kt`
- Create: `feature/reader/src/main/kotlin/com/reader/feature/reader/PublicationOpener.kt`
- Test: `feature/reader/src/test/kotlin/com/reader/feature/reader/ReaderViewModelTest.kt`

**Interfaces:**
- Consumes: `LibraryRepository` (`getBook`, `getProgress`, `saveProgress`, `markOpened`), Readium `EpubNavigatorFragment` + `Locator`.
- Produces:
  - `sealed interface ReaderUiState { object Loading; data class Ready(val publication: Publication, val initialLocator: Locator?); data class Error(val message: String); }`.
  - `ReaderViewModel`: `val uiState: StateFlow<ReaderUiState>`, `fun load(bookId: Long)`, `fun onLocatorChanged(locator: Locator)` (persists progress + percent), saving on a debounce.
  - `@Composable fun ReaderScreen(bookId: Long, onBack: () -> Unit, viewModel: ReaderViewModel = hiltViewModel())`.

- [ ] **Step 1: Create the module build file**

`feature/reader/build.gradle.kts` applies android-library + kotlin + ksp + hilt + compose, `namespace = "com.reader.feature.reader"`, depends on `:core:data`, `:core:designsystem`, Readium (`readium-shared`, `readium-streamer`, `readium-navigator`), Compose BOM, Fragment-Compose interop (`androidx.fragment:fragment-compose`), Lifecycle-ViewModel-Compose, Hilt-Navigation-Compose. Test deps: junit4, mockk, turbine, kotlinx-coroutines-test.

- [ ] **Step 2: Write the failing ViewModel test (progress persistence)**

```kotlin
package com.reader.feature.reader

import com.reader.core.data.LibraryRepository
import com.reader.core.data.model.ReadingProgress
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test
import org.readium.r2.shared.publication.Locator

class ReaderViewModelTest {
    private val repo = mockk<LibraryRepository>(relaxed = true)

    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())

    @Test fun onLocatorChanged_persists_progress() = runTest {
        val vm = ReaderViewModel(repo, mockk(relaxed = true))
        val locator = Locator(href = org.readium.r2.shared.util.Url("ch1.html")!!, mediaType = org.readium.r2.shared.util.mediatype.MediaType.XHTML, locations = Locator.Locations(totalProgression = 0.5))
        vm.onLocatorChanged(bookId = 3L, locator = locator)
        advanceUntilIdle()
        coVerify { repo.saveProgress(match<ReadingProgress> { it.bookId == 3L && it.percent == 0.5 }) }
    }
}
```

(Confirm the exact `Locator` constructor signature against the resolved Readium version; adjust the test's locator construction to match.)

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :feature:reader:testDebugUnitTest`
Expected: FAIL — unresolved `ReaderViewModel`.

- [ ] **Step 4: Implement the PublicationOpener helper**

`PublicationOpener` wraps Readium's `AssetRetriever` + `PublicationOpener`/`Streamer` to open a `Publication` from a `filePath` (suspend `open(path: String): Publication?`). It mirrors the parsing path used in Task 4 but returns the full `Publication` for rendering.

- [ ] **Step 5: Implement the ViewModel**

`ReaderViewModel`: `load(bookId)` opens the book's `Publication` via `PublicationOpener`, reads stored progress, deserializes `locatorJson` into a `Locator` (`Locator.fromJSON`), emits `Ready(publication, initialLocator)`, and calls `markOpened`. `onLocatorChanged(bookId, locator)` serializes `locator.toJSON().toString()` and saves `ReadingProgress(bookId, json, locator.locations.totalProgression ?: 0.0, now)`; debounce saves with a small delay to avoid thrashing.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :feature:reader:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 7: Implement the Reader Compose screen**

`ReaderScreen.kt` hosts `EpubNavigatorFragment` inside Compose using `AndroidFragmentView` / `fragment-compose`. On `Ready`, create the fragment via its factory with `publication` and `initialLocator`, register a listener for locator changes that calls `viewModel.onLocatorChanged(bookId, locator)`. Show a back button that calls `onBack`. While `Loading`, show a centered progress indicator; on `Error`, show the message. (Verify the current `EpubNavigatorFragment` creation API and the locator-change callback name via context7.)

- [ ] **Step 8: Build to verify it compiles**

Run: `./gradlew :feature:reader:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add feature/reader
git commit -m "feat: add reader screen with readium navigator and progress persistence"
```

---

### Task 7: App wiring — navigation host

**Files:**
- Modify: `app/src/main/kotlin/com/reader/app/MainActivity.kt`
- Create: `app/src/main/kotlin/com/reader/app/navigation/ReaderNavHost.kt`

**Interfaces:**
- Consumes: `LibraryScreen` (Task 5), `ReaderScreen` (Task 6).
- Produces: an end-to-end navigable app: Library → Reader.

- [ ] **Step 1: Implement the navigation host**

`ReaderNavHost.kt` defines a `NavHost` with two routes: `"library"` (start) renders `LibraryScreen(onBookClick = { id -> navController.navigate("reader/$id") })`, and `"reader/{bookId}"` renders `ReaderScreen(bookId = ..., onBack = { navController.popBackStack() })`, reading `bookId` from nav arguments (typed `Long`).

- [ ] **Step 2: Wire MainActivity**

`MainActivity.setContent` wraps `ReaderNavHost()` in `ReaderTheme { Surface { ... } }`.

- [ ] **Step 3: Build and install**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual smoke test**

Install on the device/emulator, import a `.epub`, confirm it appears on the shelf, open it, confirm it renders, scroll/paginate, leave and reopen, confirm it resumes at the saved location.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat: wire navigation between library and reader"
```

---

## Self-Review Notes

- **Spec coverage (Plan 1 portion):** local `.epub` import (Tasks 4–5), reading via Readium (Task 6), per-book progress persistence (Tasks 2,3,6), multi-module structure + stack (Task 0, all). Tap-to-translate, dictionary, saved words, and the full theme set are explicitly deferred to Plans 2–4 and are NOT in this plan.
- **Deferred-but-noted:** `:core:designsystem` ships only basic light/dark here; the full reading-theme set (sepia/AMOLED/typography controls) is Plan 4. The reader is usable with system theming in the meantime.
- **Version/API caveat:** Readium and AndroidX APIs evolve; Tasks 0/4/6 explicitly instruct verifying current signatures via context7 rather than hardcoding guesses. This is intentional, not a placeholder.
