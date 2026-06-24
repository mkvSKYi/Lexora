# Saved Words Implementation Plan (Plan 3a)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Save button on the translation popover persists the word/phrase to a vocabulary list, viewable and deletable on a dedicated Saved Words screen reached from the library.

**Architecture:** A new `saved_words` Room table (`:core:database`, additive migration) + `SavedWordsRepository` (`:core:data`). The translation popover raises an `onSave` intent; the reader builds a `SavedWord` (book id/title + best-effort context sentence) and persists it. A new `:feature:saved` module renders the list; the app nav adds a Saved route reached from a Library top-bar button.

**Tech Stack:** Kotlin, Compose, Material 3, Hilt, Room, Coroutines/Flow. Test: JUnit4, Robolectric (Room), MockK, Turbine, kotlinx-coroutines-test.

## Global Constraints

- minSdk 26, compileSdk/targetSdk 36. Kotlin only, Compose + Material 3. MVVM + repository, unidirectional Flow, Hilt DI.
- ALL dependency versions via the catalog `gradle/libs.versions.toml`; no hardcoded versions in module build files.
- New module `:feature:saved` (namespace `com.reader.feature.saved`).
- The Room migration MUST be additive (create `saved_words` only) — existing `books`/`reading_progress` data must survive an in-place app upgrade.
- De-duplication: a unique index on `(term, bookId)`; saving the same term+book upserts (no duplicate).
- Saving never blocks on context-sentence resolution; `contextSentence` is best-effort (null on failure / for phrase saves).
- `flowOf`/Turbine tests: drain with `cancelAndIgnoreRemainingEvents()`; `@OptIn(ExperimentalCoroutinesApi::class)` where `Dispatchers.setMain` is used.

---

### Task 1: saved_words entity + DAO + migration

**Files:**
- Create: `core/database/src/main/kotlin/com/reader/core/database/entity/SavedWordEntity.kt`
- Create: `core/database/src/main/kotlin/com/reader/core/database/dao/SavedWordDao.kt`
- Modify: `core/database/src/main/kotlin/com/reader/core/database/ReaderDatabase.kt`
- Modify: `core/database/src/main/kotlin/com/reader/core/database/di/DatabaseModule.kt`
- Test: `core/database/src/test/kotlin/com/reader/core/database/SavedWordDaoTest.kt`
- Test: `core/database/src/test/kotlin/com/reader/core/database/MigrationTest.kt`

**Interfaces:**
- Produces:
  - `SavedWordEntity(id: Long = 0, term: String, translation: String, contextSentence: String?, bookId: Long, bookTitle: String, createdAt: Long)` — `@Entity(tableName = "saved_words", indices = [Index(value=["term","bookId"], unique=true)])`, `@PrimaryKey(autoGenerate=true) id`.
  - `SavedWordDao`: `fun observeAll(): Flow<List<SavedWordEntity>>` (ordered `createdAt DESC`), `@Upsert suspend fun upsert(entity: SavedWordEntity)`, `@Query("DELETE FROM saved_words WHERE id = :id") suspend fun deleteById(id: Long)`, `@Query("SELECT EXISTS(SELECT 1 FROM saved_words WHERE term = :term AND bookId = :bookId)") suspend fun existsByTermAndBook(term: String, bookId: Long): Boolean`.
  - `ReaderDatabase` version 2 with `SavedWordEntity` added + `MIGRATION_1_2`.

- [ ] **Step 1: Write the failing DAO test (Robolectric, in-memory)**

```kotlin
package com.reader.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.reader.core.database.entity.SavedWordEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SavedWordDaoTest {
    private lateinit var db: ReaderDatabase
    private lateinit var dao: SavedWordDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), ReaderDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.savedWordDao()
    }
    @After fun teardown() = db.close()

    @Test fun upsert_then_observe_newest_first() = runTest {
        dao.upsert(SavedWordEntity(term = "dog", translation = "собака", contextSentence = "A dog ran.", bookId = 1, bookTitle = "B", createdAt = 100))
        dao.upsert(SavedWordEntity(term = "cat", translation = "кіт", contextSentence = null, bookId = 1, bookTitle = "B", createdAt = 200))
        dao.observeAll().test {
            val list = awaitItem()
            assertEquals(listOf("cat", "dog"), list.map { it.term })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun unique_term_book_upsert_replaces() = runTest {
        dao.upsert(SavedWordEntity(term = "dog", translation = "собака", contextSentence = null, bookId = 1, bookTitle = "B", createdAt = 100))
        dao.upsert(SavedWordEntity(term = "dog", translation = "пес", contextSentence = null, bookId = 1, bookTitle = "B", createdAt = 300))
        dao.observeAll().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("пес", list.first().translation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun exists_and_delete() = runTest {
        dao.upsert(SavedWordEntity(id = 0, term = "dog", translation = "собака", contextSentence = null, bookId = 1, bookTitle = "B", createdAt = 100))
        assertTrue(dao.existsByTermAndBook("dog", 1))
        val id = dao.observeAll().let { /* read one */ 0L }
        // fetch id via observe
        dao.observeAll().test {
            val saved = awaitItem().first()
            dao.deleteById(saved.id)
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(dao.existsByTermAndBook("dog", 1))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:database:testDebugUnitTest --tests "*SavedWordDaoTest*"`
Expected: FAIL — `SavedWordEntity`/`savedWordDao` unresolved.

- [ ] **Step 3: Implement the entity + DAO**

`SavedWordEntity.kt` — the `@Entity` above (table `saved_words`, unique index on `term,bookId`).
`SavedWordDao.kt` — the four methods above (`observeAll` ordered `createdAt DESC`).

- [ ] **Step 4: Bump the database to v2 + migration**

In `ReaderDatabase.kt`: add `SavedWordEntity::class` to `@Database(entities=[...], version = 2)`, add `abstract fun savedWordDao(): SavedWordDao`, and define a `MIGRATION_1_2` that runs:
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_words` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`term` TEXT NOT NULL, `translation` TEXT NOT NULL, " +
                "`contextSentence` TEXT, `bookId` INTEGER NOT NULL, " +
                "`bookTitle` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_words_term_bookId` ON `saved_words` (`term`, `bookId`)"
        )
    }
}
```
In `DatabaseModule.kt`, add `.addMigrations(ReaderDatabase.MIGRATION_1_2)` to the `databaseBuilder` (expose `MIGRATION_1_2` from a companion). Confirm the generated v2 schema JSON matches the migration's SQL (column types/order, index name) — Room's `MigrationTestHelper`/exported schema is the source of truth; adjust the SQL to match exactly if Room generates a different index name.

- [ ] **Step 5: Write the migration test**

```kotlin
package com.reader.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReaderDatabase::class.java,
    )

    @Test fun migrate_1_to_2_preserves_books_and_adds_saved_words() {
        val dbName = "migration-test"
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO books (id,title,author,coverPath,filePath,addedAt,lastOpenedAt) VALUES (1,'Dune',null,null,'/d.epub',1,null)")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 2, true, ReaderDatabase.MIGRATION_1_2)
        val booksCursor = db.query("SELECT title FROM books WHERE id = 1")
        assertTrue(booksCursor.moveToFirst())
        val savedCursor = db.query("SELECT COUNT(*) FROM saved_words")
        assertTrue(savedCursor.moveToFirst())
        db.close()
    }
}
```
(Adjust the v1 `books` insert columns to the actual v1 schema. `MigrationTestHelper` reads the exported schema JSON under `core/database/schemas/`.)

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :core:database:testDebugUnitTest`
Expected: PASS (DAO + migration + existing BookDao tests green).

- [ ] **Step 7: Commit**

```bash
git add core/database
git commit -m "feat: add saved_words table with migration"
```

---

### Task 2: SavedWord domain + repository

**Files:**
- Create: `core/data/src/main/kotlin/com/reader/core/data/model/SavedWord.kt`
- Create: `core/data/src/main/kotlin/com/reader/core/data/SavedWordsRepository.kt`
- Create: `core/data/src/main/kotlin/com/reader/core/data/mapper/SavedWordMapper.kt`
- Modify: `core/data/src/main/kotlin/com/reader/core/data/di/DataModule.kt`
- Test: `core/data/src/test/kotlin/com/reader/core/data/SavedWordsRepositoryTest.kt`

**Interfaces:**
- Consumes: `SavedWordDao`, `SavedWordEntity` (Task 1).
- Produces:
  - `data class SavedWord(id: Long, term: String, translation: String, contextSentence: String?, bookId: Long, bookTitle: String, createdAt: Long)`.
  - `interface SavedWordsRepository`: `fun observe(): Flow<List<SavedWord>>`, `suspend fun save(word: SavedWord)`, `suspend fun delete(id: Long)`.
  - `DefaultSavedWordsRepository @Inject constructor(dao: SavedWordDao)` bound in `DataModule`.

- [ ] **Step 1: Write the failing repository test**

```kotlin
package com.reader.core.data

import app.cash.turbine.test
import com.reader.core.data.model.SavedWord
import com.reader.core.database.dao.SavedWordDao
import com.reader.core.database.entity.SavedWordEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedWordsRepositoryTest {
    private val dao = mockk<SavedWordDao>(relaxed = true)
    private val repo = DefaultSavedWordsRepository(dao)

    @Test fun observe_maps_entities_to_domain() = runTest {
        coEvery { dao.observeAll() } returns flowOf(
            listOf(SavedWordEntity(5, "dog", "собака", "A dog.", 1, "Dune", 100)),
        )
        repo.observe().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("dog", list.first().term)
            assertEquals(5L, list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun save_and_delete_delegate() = runTest {
        repo.save(SavedWord(0, "dog", "собака", null, 1, "Dune", 100))
        coVerify { dao.upsert(any()) }
        repo.delete(5)
        coVerify { dao.deleteById(5) }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*SavedWordsRepositoryTest*"`
Expected: FAIL — unresolved `SavedWord`/`DefaultSavedWordsRepository`.

- [ ] **Step 3: Implement domain + mapper + repository**

`SavedWord.kt` — the data class. `SavedWordMapper.kt` — `SavedWordEntity.toDomain()` / `SavedWord.toEntity()` (field-for-field). `SavedWordsRepository.kt` — the interface + `DefaultSavedWordsRepository` mapping `dao.observeAll()` → domain, `save` → `dao.upsert(word.toEntity())`, `delete` → `dao.deleteById(id)`. Bind in `DataModule` (`@Binds`, SingletonComponent).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*SavedWordsRepositoryTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/data
git commit -m "feat: add saved words repository"
```

---

### Task 3: :feature:saved module — ViewModel + screen

**Files:**
- Create: `feature/saved/build.gradle.kts`, `feature/saved/src/main/AndroidManifest.xml`
- Create: `feature/saved/src/main/kotlin/com/reader/feature/saved/SavedWordsUiState.kt`
- Create: `feature/saved/src/main/kotlin/com/reader/feature/saved/SavedWordsViewModel.kt`
- Create: `feature/saved/src/main/kotlin/com/reader/feature/saved/SavedWordsScreen.kt`
- Modify: `settings.gradle.kts` (include `:feature:saved`)
- Test: `feature/saved/src/test/kotlin/com/reader/feature/saved/SavedWordsViewModelTest.kt`

**Interfaces:**
- Consumes: `SavedWordsRepository`, `SavedWord` (Task 2).
- Produces:
  - `sealed interface SavedWordsUiState { data object Loading; data class Content(val words: List<SavedWord>) }`.
  - `@HiltViewModel SavedWordsViewModel @Inject constructor(repo: SavedWordsRepository)`: `val uiState: StateFlow<SavedWordsUiState>`, `fun delete(id: Long)`.
  - `@Composable fun SavedWordsScreen(onBack: () -> Unit, viewModel: SavedWordsViewModel = hiltViewModel())`.

- [ ] **Step 1: Create the module build file + settings include**

`feature/saved/build.gradle.kts` mirrors `feature/library/build.gradle.kts` (android-library + kotlin + ksp + hilt + compose), `namespace = "com.reader.feature.saved"`, depends on `:core:data`, `:core:designsystem`, Compose BOM, Material3, Lifecycle-ViewModel-Compose, Hilt-Navigation-Compose. Test: junit4, mockk, turbine, coroutines-test. Add `include(":feature:saved")` to `settings.gradle.kts`. All via the catalog.

- [ ] **Step 2: Write the failing ViewModel test**

```kotlin
package com.reader.feature.saved

import app.cash.turbine.test
import com.reader.core.data.SavedWordsRepository
import com.reader.core.data.model.SavedWord
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedWordsViewModelTest {
    private val repo = mockk<SavedWordsRepository>(relaxed = true)
    @Before fun s() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun t() = Dispatchers.resetMain()

    @Test fun emits_content_from_repo() = runTest {
        every { repo.observe() } returns flowOf(
            listOf(SavedWord(1, "dog", "собака", null, 1, "Dune", 100)),
        )
        val vm = SavedWordsViewModel(repo)
        vm.uiState.test {
            assertEquals(SavedWordsUiState.Loading, awaitItem())
            val c = awaitItem()
            assertTrue(c is SavedWordsUiState.Content)
            assertEquals(1, (c as SavedWordsUiState.Content).words.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun delete_delegates() = runTest {
        every { repo.observe() } returns flowOf(emptyList())
        val vm = SavedWordsViewModel(repo)
        vm.delete(7)
        coVerify { repo.delete(7) }
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :feature:saved:testDebugUnitTest`
Expected: FAIL — unresolved `SavedWordsViewModel`.

- [ ] **Step 4: Implement UI state + ViewModel + screen**

`SavedWordsUiState.kt` — the sealed interface. `SavedWordsViewModel` (`@HiltViewModel`) maps `repo.observe()` → `Content`, `stateIn(viewModelScope, WhileSubscribed(5000), Loading)`; `delete(id)` launches `repo.delete(id)`.
`SavedWordsScreen.kt` — `Scaffold` with a top app bar (title "Saved Words" + back icon `onBack`), body: when Content non-empty a `LazyColumn` of rows (term `titleMedium`/bold; translation `bodyLarge`; `contextSentence` muted `bodySmall` if present; book title + date `labelSmall`; a trailing delete `IconButton` calling `viewModel.delete(word.id)`); empty Content → centered "No saved words yet"; Loading → progress indicator.

- [ ] **Step 5: Run the tests + build**

Run: `./gradlew :feature:saved:testDebugUnitTest && ./gradlew :feature:saved:assembleDebug`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add feature/saved settings.gradle.kts
git commit -m "feat: add saved words screen and view model"
```

---

### Task 4: Save button on the popover + reader save wiring

**Files:**
- Modify: `feature/translation/src/main/kotlin/com/reader/feature/translation/TranslationPopover.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/SelectionEvent.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/WordResolver.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/EpubReaderFragment.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderViewModel.kt`
- Modify: `feature/reader/src/main/kotlin/com/reader/feature/reader/ReaderScreen.kt`
- Modify: `feature/reader/build.gradle.kts` (already depends on `:core:data`)

**Interfaces:**
- Consumes: `SavedWordsRepository` (Task 2), `TranslationPopupState.Result` (`source`, `translation`).
- Produces:
  - `TranslationPopover(state, onDismiss, onSave: () -> Unit, modifier = Modifier)` — a Save button in the `Result` branch.
  - `SelectionEvent(text, rectInView, contextSentence: String?)` — word taps carry the enclosing sentence; phrase long-press carries null.
  - `ReaderViewModel`: `val bookTitle: StateFlow<String?>`; `fun saveCurrentWord(term: String, translation: String, contextSentence: String?)` — builds a `SavedWord(0, term, translation, contextSentence, bookId, bookTitle, now)` and calls `savedWordsRepository.save(...)`.

- [ ] **Step 1: Add the Save button to the popover**

In `TranslationPopover`, add `onSave: () -> Unit` param; in the `Result` branch render a small text/icon Save button below the translation that calls `onSave()`. Keep Loading/Error unchanged. (No persistence here.)

- [ ] **Step 2: Carry the context sentence on word taps**

Extend `SelectionEvent` with `val contextSentence: String?`. In `WordResolver`, after resolving the word, also compute the enclosing sentence (reuse the sentence-boundary scan from `SentenceResolver`: expand from the caret to terminators `.!?…`) and return it as `sentence` in the JSON. In `EpubReaderFragment.onTapResolveWord`, parse `sentence` and pass it as `SelectionEvent(word, rect, contextSentence = sentence)`. For the long-press sentence path, pass `contextSentence = null` (the term IS the sentence).

- [ ] **Step 3: Expose bookTitle + saveCurrentWord on the ViewModel**

In `ReaderViewModel`, expose `bookTitle: StateFlow<String?>` (from the loaded `Book` title or `publication.metadata.title`; set on Ready). Inject `SavedWordsRepository`. Add `fun saveCurrentWord(term, translation, contextSentence)` that reads the current `bookId` (the ViewModel already has it from `load`) + `bookTitle.value` and calls `savedWordsRepository.save(SavedWord(0, term, translation, contextSentence, bookId, bookTitle ?: "", System.currentTimeMillis()))` in `viewModelScope`.

- [ ] **Step 4: Wire Save in ReaderScreen**

`ReaderScreen` already stashes the latest `SelectionEvent` rect; also stash its `contextSentence`. Pass `onSave` to `TranslationPopover`: when the popup state is `Result`, `onSave = { translationVm ...; readerViewModel.saveCurrentWord(result.source, result.translation, lastContextSentence); translationVm.dismiss() }`. Show a brief confirmation (snackbar or a transient "Saved" — minimal). Preserve the existing translation/anchor/dismiss behavior.

- [ ] **Step 5: Build + on-device check (save persists)**

Build + install. Translate a word → tap Save. Verify the row lands in the DB:
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:installDebug
# after saving a word in the app:
~/Library/Android/sdk/platform-tools/adb shell "run-as com.reader.app sqlite3 databases/reader.db 'SELECT term,translation,contextSentence FROM saved_words'" 2>/dev/null || echo "(verify via the Saved screen in Task 5 instead)"
```
Expected: the saved term + translation (+ context for a word tap) is present. (If `run-as sqlite3` isn't available, defer the visual confirmation to Task 5's Saved screen.)

- [ ] **Step 6: Commit**

```bash
git add feature/translation feature/reader
git commit -m "feat: save translated words from the reader popover"
```

---

### Task 5: App navigation — Saved button + route + on-device smoke

**Files:**
- Modify: `feature/library/src/main/kotlin/com/reader/feature/library/LibraryScreen.kt`
- Modify: `app/src/main/kotlin/com/reader/app/navigation/ReaderNavHost.kt`
- Modify: `app/build.gradle.kts` (depend on `:feature:saved`)

**Interfaces:**
- Consumes: `SavedWordsScreen` (Task 3).
- Produces: a `saved` nav route reachable from a Library top-bar "Saved" button.

- [ ] **Step 1: Add a Saved button to the Library top bar**

In `LibraryScreen`, add an `onOpenSaved: () -> Unit` param and a top-bar action `IconButton` (a bookmark/list icon, e.g. `Icons.Filled.Bookmarks`) that calls it. Update the `LibraryScreen` call site.

- [ ] **Step 2: Add the saved route**

In `ReaderNavHost`, add `composable("saved") { SavedWordsScreen(onBack = { navController.popBackStack() }) }`, and pass `onOpenSaved = { navController.navigate("saved") }` to `LibraryScreen`. Add `implementation(project(":feature:saved"))` to `app/build.gradle.kts`.

- [ ] **Step 3: Build + full on-device smoke test**

Build + install on the S25 Ultra. End-to-end:
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:installDebug
~/Library/Android/sdk/platform-tools/adb shell am start -n com.reader.app/.MainActivity
```
Manually/adb: open a book → tap a word → in the popover tap **Save** → back to the library → tap the **Saved** button → the saved word appears with its translation + context + book → tap delete → it disappears. Re-save the same word → no duplicate. Confirm the existing library/books still load after the DB upgrade (migration preserved data). Screenshot the Saved screen to `…/scratchpad/saved_*.png`. Check logcat for FATAL.

- [ ] **Step 4: Commit**

```bash
git add feature/library app
git commit -m "feat: reach saved words screen from the library"
```

---

## Self-Review Notes

- **Spec coverage:** Save button on popover (Task 4); persist term/translation/context/book/createdAt (Tasks 1,2,4); de-dup unique index + upsert (Task 1); Saved screen list + delete + empty state (Task 3); access from Library + route (Task 5); additive migration preserving data (Task 1 + Task 5 on-device). Dictionary fields (IPA/POS/definitions) are Plan 3b and absent by design.
- **Type consistency:** `SavedWordEntity`/`SavedWord(id,term,translation,contextSentence,bookId,bookTitle,createdAt)`, `SavedWordDao.{observeAll,upsert,deleteById,existsByTermAndBook}`, `SavedWordsRepository.{observe,save,delete}`, `SavedWordsUiState.{Loading,Content(words)}`, `SavedWordsViewModel.{uiState,delete}`, `TranslationPopover(... onSave ...)`, `SelectionEvent(text,rectInView,contextSentence)`, `ReaderViewModel.{bookTitle, saveCurrentWord}` are used consistently across tasks.
- **Risk:** the Room migration (Task 1) must match Room's generated v2 schema exactly (index name/column order) — the migration test guards this; verify the exported schema. Context-sentence capture (Task 4) reuses Plan 2's sentence-boundary logic; null on failure, never blocks the save.
- **Verification:** Task 5 ends with a full on-device round-trip (save → list → delete, no duplicate, books survive upgrade), per the Plan-1 lesson.
