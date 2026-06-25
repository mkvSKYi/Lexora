# Saved Words Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign Saved Words into a premium Aurora screen with a `learned` flag (learned cards turn green), a stats header with learned progress, All/Learning/Learned filter chips, and tap-a-word-to-see-its-dictionary-definitions.

**Architecture:** A `learned` column is added via a Room v2→v3 migration. `SavedWordsViewModel` exposes the full list + learned/total counts and a filter; the redesigned `SavedWordsScreen` (LazyColumn over a dark aurora canvas) renders the stats card, filter chips, and aurora word cards, reusing `WordDictionarySheet` (gaining a `showSave` flag) to show definitions on tap.

**Tech Stack:** Kotlin, Compose, Material 3, Hilt, Room, Coroutines. Test: JUnit4, Robolectric (Room migration + in-memory), MockK, Turbine, kotlinx-coroutines-test.

## Global Constraints

- minSdk 26, compileSdk/targetSdk 36. Kotlin + Compose + Material 3. Hilt DI, MVVM, unidirectional Flow. ALL versions via the catalog; no hardcoded versions in module build files.
- DB migration MUST preserve existing saved words; `learned` defaults to `false` (`INTEGER NOT NULL DEFAULT 0`). Bump `ReaderDatabase` `version` 2 → 3 and register `MIGRATION_2_3`.
- Aurora accent `Color(0xFF9B8CFF)` (redefine locally in `:feature:saved`, matching the other modules); learned green `Color(0xFF34C759)`.
- Reuse `WordDictionarySheet` for the definitions sheet; add `showSave: Boolean = true` (reader keeps true, saved-words passes false). The reader's Save path must NOT change.
- Existing signatures: `SavedWordEntity(id, term, translation, contextSentence?, bookId, bookTitle, createdAt)`; `SavedWord` domain (same fields); `SavedWordDao` (`observeAll`, `upsert`, `deleteById`, `deleteByBookId`, `existsByTermAndBook`); `ReaderDatabase` `version=2` + `MIGRATION_1_2` + `DatabaseModule.addMigrations(MIGRATION_1_2)`; `SavedWordsRepository` (`observe`, `save`, `delete`); `SavedWordsViewModel(repo)` exposes `uiState`/`delete`; `SavedWordsUiState{Loading, Content(words)}`; `SavedWordsScreen(onBack, viewModel)`; `WordLookupViewModel.onWord(word)`/`dismiss`; `WordDictionarySheet(state, onSave, onDismiss)`.

---

### Task 1: `learned` flag — entity, migration, DAO, repository

**Files:**
- Modify: `core/database/src/main/kotlin/com/reader/core/database/entity/SavedWordEntity.kt`
- Modify: `core/database/src/main/kotlin/com/reader/core/database/ReaderDatabase.kt`
- Modify: `core/database/src/main/kotlin/com/reader/core/database/dao/SavedWordDao.kt`
- Modify: `core/database/src/main/kotlin/com/reader/core/database/di/DatabaseModule.kt`
- Modify: `core/data/src/main/kotlin/com/reader/core/data/model/SavedWord.kt`
- Modify: `core/data/src/main/kotlin/com/reader/core/data/mapper/SavedWordMapper.kt`
- Modify: `core/data/src/main/kotlin/com/reader/core/data/SavedWordsRepository.kt`
- Test: `core/database/src/test/kotlin/com/reader/core/database/SavedWordsMigrationTest.kt`
- Test: `core/data/src/test/kotlin/com/reader/core/data/SavedWordsLearnedTest.kt`

**Interfaces:**
- Produces: `SavedWordEntity.learned: Boolean`; `SavedWord.learned: Boolean`; `ReaderDatabase.MIGRATION_2_3` + `version = 3`; `SavedWordDao.updateLearned(id: Long, learned: Boolean)`; `SavedWordsRepository.markLearned(id: Long, learned: Boolean)`.

- [ ] **Step 1: Add the column to the entity + domain + mappers**

`SavedWordEntity.kt` — add `val learned: Boolean = false,` as the last constructor field.
`SavedWord.kt` — add `val learned: Boolean,` as the last field.
`SavedWordMapper.kt` — in both `toDomain()` and `toEntity()` add `learned = learned,`.

- [ ] **Step 2: Bump the DB version + add the migration**

In `ReaderDatabase.kt`: change `version = 2` to `version = 3`; in the companion object, after `MIGRATION_1_2`, add:
```kotlin
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `saved_words` ADD COLUMN `learned` INTEGER NOT NULL DEFAULT 0")
            }
        }
```
In `DatabaseModule.kt`: change `.addMigrations(ReaderDatabase.MIGRATION_1_2)` to `.addMigrations(ReaderDatabase.MIGRATION_1_2, ReaderDatabase.MIGRATION_2_3)`.

- [ ] **Step 3: Add the DAO update + repository method**

`SavedWordDao.kt` — add:
```kotlin
    @Query("UPDATE saved_words SET learned = :learned WHERE id = :id")
    suspend fun updateLearned(id: Long, learned: Boolean)
```
`SavedWordsRepository.kt` — add `suspend fun markLearned(id: Long, learned: Boolean)` to the interface, and to the impl:
```kotlin
    override suspend fun markLearned(id: Long, learned: Boolean) = dao.updateLearned(id, learned)
```

- [ ] **Step 4: Write the failing tests**

`SavedWordsMigrationTest.kt` (Robolectric, file-based DB so the migration runs):
```kotlin
package com.reader.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SavedWordsMigrationTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-saved-test.db"

    @After fun cleanup() { ctx.deleteDatabase(dbName) }

    @Test fun migration_2_3_preserves_rows_and_defaults_learned_false() = runTest {
        ctx.deleteDatabase(dbName)
        // Build a v2 saved_words table (matching MIGRATION_1_2) + a row, at user_version 2.
        val raw = ctx.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_words` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `term` TEXT NOT NULL, " +
                "`translation` TEXT NOT NULL, `contextSentence` TEXT, `bookId` INTEGER NOT NULL, " +
                "`bookTitle` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
        raw.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_words_term_bookId` " +
                "ON `saved_words` (`term`, `bookId`)",
        )
        raw.execSQL(
            "INSERT INTO saved_words (term, translation, contextSentence, bookId, bookTitle, createdAt) " +
                "VALUES ('dog','собака',NULL,1,'Book',5)",
        )
        raw.version = 2
        raw.close()

        val db = Room.databaseBuilder(ctx, ReaderDatabase::class.java, dbName)
            .addMigrations(ReaderDatabase.MIGRATION_1_2, ReaderDatabase.MIGRATION_2_3)
            .build()
        val rows = db.savedWordDao().observeAll().first()
        assertEquals(1, rows.size)
        assertEquals("dog", rows[0].term)
        assertFalse(rows[0].learned)
        db.close()
    }
}
```

`SavedWordsLearnedTest.kt` (in-memory Room round-trip for the DAO/repo):
```kotlin
package com.reader.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.reader.core.data.model.SavedWord
import com.reader.core.database.ReaderDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SavedWordsLearnedTest {
    private lateinit var db: ReaderDatabase
    private lateinit var repo: DefaultSavedWordsRepository

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, ReaderDatabase::class.java).allowMainThreadQueries().build()
        repo = DefaultSavedWordsRepository(db.savedWordDao())
    }
    @After fun tearDown() = db.close()

    @Test fun markLearned_updates_the_row() = runTest {
        repo.save(SavedWord(0, "dog", "собака", null, 1, "Book", 5, learned = false))
        val id = repo.observe().first().first().id
        repo.markLearned(id, true)
        assertTrue(repo.observe().first().first().learned)
    }
}
```

- [ ] **Step 5: Run the tests to verify they fail**

Run: `./gradlew :core:database:testDebugUnitTest --tests "*SavedWordsMigrationTest*" :core:data:testDebugUnitTest --tests "*SavedWordsLearnedTest*"`
Expected: FAIL — `learned`/`updateLearned`/`markLearned`/`MIGRATION_2_3` unresolved.

(Implement Steps 1–3, then re-run.)

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :core:database:testDebugUnitTest --tests "*SavedWordsMigrationTest*" :core:data:testDebugUnitTest --tests "*SavedWordsLearnedTest*"` then `./gradlew :core:database:assembleDebug`
Expected: PASS; KSP validates the v3 schema + the new query.

- [ ] **Step 7: Commit**

```bash
git add core/database core/data
git commit -m "feat: add learned flag to saved words with room migration v2 to v3"
```

---

### Task 2: ViewModel — filter + stats + toggle learned

**Files:**
- Modify: `feature/saved/src/main/kotlin/com/reader/feature/saved/SavedWordsUiState.kt`
- Create: `feature/saved/src/main/kotlin/com/reader/feature/saved/SavedWordsFilter.kt`
- Modify: `feature/saved/src/main/kotlin/com/reader/feature/saved/SavedWordsViewModel.kt`
- Test: `feature/saved/src/test/kotlin/com/reader/feature/saved/SavedWordsViewModelTest.kt`

**Interfaces:**
- Consumes: `SavedWordsRepository.observe()`, `markLearned(id, learned)`, `delete(id)`; `SavedWord` (now with `learned`).
- Produces:
  - `enum class SavedWordsFilter { ALL, LEARNING, LEARNED }`.
  - `SavedWordsUiState.Content(val words: List<SavedWord>, val learnedCount: Int, val totalCount: Int)`.
  - `SavedWordsViewModel`: `uiState: StateFlow<SavedWordsUiState>` (visible list per filter + counts from the full list), `filter: StateFlow<SavedWordsFilter>`, `setFilter(f)`, `toggleLearned(id, learned)`, `delete(id)`.

- [ ] **Step 1: Write the failing test**

`SavedWordsViewModelTest.kt`:
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedWordsViewModelTest {
    private val repo = mockk<SavedWordsRepository>(relaxed = true)
    private fun word(id: Long, learned: Boolean) =
        SavedWord(id, "t$id", "п$id", null, 1, "Book", id, learned)

    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun content_carries_filtered_list_and_full_counts() = runTest {
        every { repo.observe() } returns flowOf(listOf(word(1, true), word(2, false), word(3, true)))
        val vm = SavedWordsViewModel(repo)
        vm.uiState.test {
            assertEquals(SavedWordsUiState.Loading, awaitItem())
            val all = awaitItem() as SavedWordsUiState.Content
            assertEquals(3, all.words.size)
            assertEquals(2, all.learnedCount)
            assertEquals(3, all.totalCount)
            vm.setFilter(SavedWordsFilter.LEARNED)
            val learned = awaitItem() as SavedWordsUiState.Content
            assertEquals(listOf(1L, 3L), learned.words.map { it.id })
            assertEquals(2, learned.learnedCount) // counts from the FULL list
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun toggleLearned_delegates_to_repository() = runTest {
        every { repo.observe() } returns flowOf(emptyList())
        val vm = SavedWordsViewModel(repo)
        vm.toggleLearned(7, true)
        advanceUntilIdle()
        coVerify(exactly = 1) { repo.markLearned(7, true) }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :feature:saved:testDebugUnitTest --tests "*SavedWordsViewModelTest*"`
Expected: FAIL — `SavedWordsFilter`/new `Content` fields/`setFilter`/`toggleLearned` unresolved.

- [ ] **Step 3: Implement the state + filter + ViewModel**

`SavedWordsFilter.kt`:
```kotlin
package com.reader.feature.saved

enum class SavedWordsFilter { ALL, LEARNING, LEARNED }
```
`SavedWordsUiState.kt`:
```kotlin
package com.reader.feature.saved

import com.reader.core.data.model.SavedWord

sealed interface SavedWordsUiState {
    data object Loading : SavedWordsUiState
    data class Content(
        val words: List<SavedWord>,
        val learnedCount: Int,
        val totalCount: Int,
    ) : SavedWordsUiState
}
```
`SavedWordsViewModel.kt`:
```kotlin
package com.reader.feature.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.core.data.SavedWordsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedWordsViewModel @Inject constructor(
    private val repo: SavedWordsRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(SavedWordsFilter.ALL)
    val filter: StateFlow<SavedWordsFilter> = _filter.asStateFlow()

    val uiState: StateFlow<SavedWordsUiState> =
        combine(repo.observe(), _filter) { words, filter ->
            val visible = when (filter) {
                SavedWordsFilter.ALL -> words
                SavedWordsFilter.LEARNING -> words.filter { !it.learned }
                SavedWordsFilter.LEARNED -> words.filter { it.learned }
            }
            SavedWordsUiState.Content(
                words = visible,
                learnedCount = words.count { it.learned },
                totalCount = words.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedWordsUiState.Loading)

    fun setFilter(filter: SavedWordsFilter) { _filter.value = filter }

    fun toggleLearned(id: Long, learned: Boolean) {
        viewModelScope.launch { repo.markLearned(id, learned) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :feature:saved:testDebugUnitTest --tests "*SavedWordsViewModelTest*"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add feature/saved
git commit -m "feat: add filter, stats and learned toggle to saved words view model"
```

---

### Task 3: UI — aurora screen, learned-green cards, tap-to-define + on-device

**Files:**
- Modify: `feature/translation/src/main/kotlin/com/reader/feature/translation/WordDictionarySheet.kt`
- Modify: `feature/saved/build.gradle.kts`
- Modify: `feature/saved/src/main/kotlin/com/reader/feature/saved/SavedWordsScreen.kt`

**Interfaces:**
- Consumes: `SavedWordsViewModel` (`uiState`/`filter`/`setFilter`/`toggleLearned`/`delete`) (Task 2); `SavedWord.learned` (Task 1); `WordLookupViewModel`/`WordDictionarySheet` (`:feature:translation`).
- Produces: `WordDictionarySheet(state, onSave, onDismiss, showSave: Boolean = true)`; the redesigned `SavedWordsScreen`.

- [ ] **Step 1: Add `showSave` to WordDictionarySheet**

In `WordDictionarySheet.kt` add a `showSave: Boolean = true` parameter to the public composable. In `EntryContent` and `MachineContent`, gate each `Button(... "Save")` on `if (showSave && saveValue != null)` / `if (showSave)`. Thread `showSave` from `WordDictionarySheet` into the `EntryContent`/`MachineContent` calls. (Default true → the reader's call site is unchanged.)

- [ ] **Step 2: Add the translation dependency**

In `feature/saved/build.gradle.kts` add `implementation(project(":feature:translation"))`. Confirm `:feature:translation` does NOT depend on `:feature:saved` (no cycle) — it doesn't.

- [ ] **Step 3: Redesign SavedWordsScreen**

Rewrite `SavedWordsScreen.kt` so the body is a `LazyColumn` over the dark background with a top aurora glow (a `Box` with a `Brush.verticalGradient` of low-alpha accent → transparent behind the header), consistent with the library. Structure:
- Define local `private val Accent = Color(0xFF9B8CFF)` and `private val LearnedGreen = Color(0xFF34C759)`.
- A `WordLookupViewModel` via `hiltViewModel()`; `val wordState by wordVm.lookupState.collectAsStateWithLifecycle()`; `val filter by viewModel.filter.collectAsStateWithLifecycle()`.
- Keep the top app bar back button (or a large header row with a back icon).
- `LazyColumn` items:
  - **Header** + (when `totalCount > 0`) a **StatsCard**: an elevated `Card` (rounded 24dp) showing `learnedCount` / `totalCount` and a `LinearProgressIndicator(progress = { learnedCount / totalCount })` in `Accent`, with a label like "12 of 40 learned".
  - **FilterChips** row: three `FilterChip`s (`All`/`Learning`/`Learned`) calling `viewModel.setFilter(...)`, `selected = filter == ...`.
  - The visible `words` via `items(words, key = { it.id })` → `SavedWordCard`.
- `SavedWordCard(word, onToggleLearned, onDelete, onTap)`: an elevated `Card` (rounded 20dp); `containerColor` = `LearnedGreen.copy(alpha = 0.18f)` when `word.learned` else `surfaceVariant`; `Modifier.clickable { onTap() }`. Inside: a `Row` — a `Column(weight 1f)` with term (`titleMedium` bold), translation (`bodyLarge`, `Accent`), context sentence (quoted `"…"`, `bodySmall`, `onSurfaceVariant`, italic, max 2 lines) when non-null, and a footer "$bookTitle · $date" (`labelSmall`, muted); then an `IconButton` toggling learned (`Icons.Filled.CheckCircle` tinted `LearnedGreen` when learned, else `Icons.Outlined.CheckCircle` muted) → `onToggleLearned(!word.learned)`; and a delete `IconButton` (`Icons.Default.Delete`).
- Wire `onTap = { wordVm.onWord(word.term) }`, `onToggleLearned = { learned -> viewModel.toggleLearned(word.id, learned) }`, `onDelete = { viewModel.delete(word.id) }`.
- When `wordState != null`, render `WordDictionarySheet(state = wordState!!, onSave = { _, _ -> }, onDismiss = { wordVm.dismiss() }, showSave = false)`.
- Redesign `EmptySavedWords`: an aurora-ringed glyph (e.g. `Icons.Filled.Bookmark`) + "No saved words yet" + a muted one-liner. Keep a small "nothing here" row for an empty FILTERED result (visible list empty but `totalCount > 0`).
- Keep `formatDate` as-is.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: On-device smoke (S25 Ultra, serial RZCY51G1D6D; adb `~/Library/Android/sdk/platform-tools/adb`)**

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:installDebug
```
Open the app → Library → the bookmarks icon (top right) → Saved Words. VERIFY (screenshots, `sips -Z 1000`): the aurora header + stats card + filter chips render; word cards are premium (term/translation/context/footer); tapping the **check** toggles a card **green** and bumps the progress; the **filter** chips switch the list (All/Learning/Learned); **tapping a card** opens the dictionary sheet with the word's definitions (no Save button); **delete** still removes a word. Import-and-save a couple of words first if the list is empty (read a book, tap a word, Save). Check logcat for FATAL. (DB note: the device has no `sqlite3` — pull `reader.db`+`-wal` and query locally if needed. The app must migrate the existing `saved_words` without data loss — verify the previously-saved words still appear.)

- [ ] **Step 6: Commit**

```bash
git add feature/saved feature/translation
git commit -m "feat: aurora saved words screen with learned, stats, filter and definitions"
```

---

## Self-Review Notes

- **Spec coverage:** `learned` column + migration v2→v3 preserving rows (Task 1); DAO/repo `markLearned` (Task 1); ViewModel filter + stats + toggle (Task 2); aurora screen with stats card, filter chips, learned-green cards, tap-to-define, empty state (Task 3); `WordDictionarySheet.showSave` (Task 3); `:feature:saved`→`:feature:translation` dep (Task 3). Tests: migration row-survival + default, repo round-trip, VM filter/stats/toggle, on-device smoke.
- **Type consistency:** `SavedWordEntity.learned`/`SavedWord.learned`/`MIGRATION_2_3`/`version=3`/`updateLearned(id,learned)`/`markLearned(id,learned)`, `SavedWordsFilter{ALL,LEARNING,LEARNED}`, `SavedWordsUiState.Content(words, learnedCount, totalCount)`, `SavedWordsViewModel.{uiState,filter,setFilter,toggleLearned,delete}`, `WordDictionarySheet(state,onSave,onDismiss,showSave=true)` are used consistently. `SavedWord` now has 8 fields (learned last) — update every constructor call (saveCurrentWord in the reader builds a `SavedWord`; add `learned = false` there).
- **Risk:** the migration is the one irreversible step — Task 1's test builds a real v2 DB, runs the migration, and asserts row survival + default. The `:feature:saved`→`:feature:translation` dependency must not cycle (it doesn't). `SavedWord`'s new field means every `SavedWord(...)` constructor must pass `learned` — Task 1 Step 1 implementer must grep for `SavedWord(` call sites (notably the reader's `ReaderViewModel.saveCurrentWord`) and add `learned = false`.
