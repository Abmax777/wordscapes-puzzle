package com.wordscapes.puzzle.ui.game

import androidx.lifecycle.SavedStateHandle
import com.wordscapes.puzzle.data.level.LevelDto
import com.wordscapes.puzzle.data.level.LevelMapper
import com.wordscapes.puzzle.data.level.PlacedWordDto
import com.wordscapes.puzzle.domain.model.Level
import com.wordscapes.puzzle.domain.model.WordResult
import com.wordscapes.puzzle.domain.repository.LevelCatalog
import com.wordscapes.puzzle.domain.repository.WordLookup
import com.wordscapes.puzzle.domain.usecase.ValidateWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** viewModelScope runs on Dispatchers.Main, which does not exist off-device. */
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun level(id: Int) = LevelMapper.toDomain(
        LevelDto(
            id = id,
            letters = listOf("S", "T", "A", "R", "E"),
            gridWidth = 5,
            gridHeight = 5,
            words = listOf(
                PlacedWordDto("STARE", 0, 0, true),
                PlacedWordDto("RATE", 0, 3, false),
            ),
        ),
    )

    private class FakeCatalog(private val levels: List<Level>) : LevelCatalog {
        override suspend fun getLevels() = levels
        override suspend fun getLevel(id: Int) = levels.firstOrNull { it.id == id }
        override suspend fun levelCount() = levels.size
        override suspend fun nextLevelId(id: Int): Int? {
            val i = levels.indexOfFirst { it.id == id }
            return if (i == -1 || i == levels.lastIndex) null else levels[i + 1].id
        }
    }

    private class FakeLookup(private val words: Set<String>) : WordLookup {
        override suspend fun contains(word: String) = word.uppercase() in words
    }

    private fun viewModel(
        levelId: Int = 1,
        levels: List<Level> = listOf(level(1), level(2)),
        saved: SavedStateHandle = SavedStateHandle(mapOf("levelId" to levelId)),
    ) = GameViewModel(
        levelCatalog = FakeCatalog(levels),
        validateWord = ValidateWord(FakeLookup(setOf("STARE", "RATE", "TEARS"))),
        savedStateHandle = saved,
    )

    // ── Loading ──────────────────────────────────────────────────────────────

    @Test
    fun `loads the level named by the saved state argument`() = runTest(dispatcher) {
        val vm = viewModel(levelId = 2)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.level?.id)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `resolves the next level id`() = runTest(dispatcher) {
        val vm = viewModel(levelId = 1)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.nextLevelId)
    }

    @Test
    fun `the final level has no next`() = runTest(dispatcher) {
        val vm = viewModel(levelId = 2)
        advanceUntilIdle()
        assertNull(vm.uiState.value.nextLevelId)
        assertFalse(vm.uiState.value.hasNextLevel)
    }

    @Test
    fun `a missing level surfaces an error rather than crashing`() = runTest(dispatcher) {
        val vm = viewModel(levelId = 99)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error != null)
        assertFalse(vm.uiState.value.isLoading)
    }

    // ── Submissions ──────────────────────────────────────────────────────────

    @Test
    fun `a grid word is revealed`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.submitWord("STARE")
        advanceUntilIdle()
        assertEquals(setOf(0), vm.uiState.value.revealedWordIndices)
        assertTrue(vm.uiState.value.lastResult is WordResult.GridWord)
    }

    @Test
    fun `a bonus word is collected without touching the grid`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.submitWord("TEARS")
        advanceUntilIdle()
        assertEquals(setOf("TEARS"), vm.uiState.value.foundBonusWords)
        assertTrue(vm.uiState.value.revealedWordIndices.isEmpty())
    }

    @Test
    fun `an invalid word changes nothing but the feedback`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.submitWord("TSR")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.revealedWordIndices.isEmpty())
        assertTrue(vm.uiState.value.lastResult is WordResult.Invalid)
    }

    /**
     * The race the Channel exists to prevent. Both submissions are queued
     * before either is processed; without serialisation both would read the
     * same empty "revealed" snapshot and resolve as fresh grid words.
     */
    @Test
    fun `the same word submitted twice in a burst resolves as already found`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.submitWord("STARE")
            vm.submitWord("STARE")
            advanceUntilIdle()

            assertEquals(setOf(0), vm.uiState.value.revealedWordIndices)
            assertTrue(
                "second submission should be AlreadyFound, was ${vm.uiState.value.lastResult}",
                vm.uiState.value.lastResult is WordResult.AlreadyFound,
            )
        }

    @Test
    fun `a burst of distinct words is processed in order and none are dropped`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.submitWord("STARE")
            vm.submitWord("TEARS")
            vm.submitWord("RATE")
            advanceUntilIdle()

            assertEquals(setOf(0, 1), vm.uiState.value.revealedWordIndices)
            assertEquals(setOf("TEARS"), vm.uiState.value.foundBonusWords)
        }

    @Test
    fun `submission id increments once per submission`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(0L, vm.uiState.value.submissionId)

        vm.submitWord("TSR")
        advanceUntilIdle()
        assertEquals(1L, vm.uiState.value.submissionId)

        vm.submitWord("TSR")
        advanceUntilIdle()
        assertEquals(
            "identical repeated results must still be distinguishable to animations",
            2L, vm.uiState.value.submissionId,
        )
    }

    // ── Completion ───────────────────────────────────────────────────────────

    @Test
    fun `the level completes only when every grid word is revealed`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.submitWord("STARE")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isComplete)

        vm.submitWord("RATE")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isComplete)
        assertEquals(2, vm.uiState.value.wordsFound)
        assertEquals(2, vm.uiState.value.wordsTotal)
    }

    @Test
    fun `bonus words alone never complete the level`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.submitWord("TEARS")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isComplete)
    }

    // ── Saved state ──────────────────────────────────────────────────────────

    @Test
    fun `progress survives a rebuild from the same SavedStateHandle`() =
        runTest(dispatcher) {
            val saved = SavedStateHandle(mapOf("levelId" to 1))
            val first = viewModel(saved = saved)
            advanceUntilIdle()
            first.submitWord("STARE")
            first.submitWord("TEARS")
            advanceUntilIdle()

            // Simulates process death: same handle, brand new ViewModel.
            val restored = viewModel(saved = saved)
            advanceUntilIdle()

            assertEquals(setOf(0), restored.uiState.value.revealedWordIndices)
            assertEquals(setOf("TEARS"), restored.uiState.value.foundBonusWords)
        }

    @Test
    fun `a fresh handle starts empty`() = runTest(dispatcher) {
        val vm = viewModel(saved = SavedStateHandle(mapOf("levelId" to 1)))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.revealedWordIndices.isEmpty())
        assertTrue(vm.uiState.value.foundBonusWords.isEmpty())
    }

    @Test
    fun `consuming feedback clears it without losing progress`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.submitWord("STARE")
        advanceUntilIdle()

        vm.consumeFeedback()
        assertNull(vm.uiState.value.lastResult)
        assertEquals(setOf(0), vm.uiState.value.revealedWordIndices)
    }
}
