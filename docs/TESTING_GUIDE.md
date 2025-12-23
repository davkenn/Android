# Testing Guide

## Quick Start

### What Type of Test Should I Write?

| Scenario | Test Type | Template | Copy Command |
|----------|-----------|----------|--------------|
| Business logic in ViewModel | ViewModel Unit Test | `ViewModelTestTemplate.kt` | `cp app/src/test/.../templates/ViewModelTestTemplate.kt app/src/test/.../MyViewModelTest.kt` |
| Activity + ViewModel integration | Activity Integration Test | `ActivityIntegrationTestTemplate.kt` | `cp app/src/test/.../templates/ActivityIntegrationTestTemplate.kt app/src/test/.../MyActivityTest.kt` |
| Flow emission sequences | Flow Test | `FlowTestTemplate.kt` | `cp app/src/test/.../templates/FlowTestTemplate.kt app/src/test/.../MyFlowTest.kt` |
| Complex user journey | Recording-Based Test | `RecordingBasedTestTemplate.kt` | See [Flow Recording Guide](FLOW_RECORDING.md) |

### Decision Tree

```
Need to write a test?
│
├─ Testing business logic only? → ViewModel Unit Test ⭐ (Most common)
│
├─ Testing UI + Activity lifecycle? → Activity Integration Test
│
├─ Testing Flow emissions over time? → Flow Test (with Turbine)
│
└─ Reproducing complex real user flow? → Recording-Based Test
```

## Writing a ViewModel Unit Test

**Most common test type** - Use this for testing state transformations, async operations, and business rules.

### 1. Copy the Template

```bash
cp app/src/test/java/protect/card_locker/templates/ViewModelTestTemplate.kt \
   app/src/test/java/protect/card_locker/viewmodels/MyViewModelTest.kt
```

### 2. Replace Placeholders

- `YourViewModel` → your ViewModel class
- `YourRepository` → your repository/dependency
- `FakeYourRepository` → your fake implementation
- `YourState` → your state sealed interface

### 3. Key Pattern

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MyViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule() // Required!

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `test something`() = runTest(testDispatcher) {
        // Arrange
        val fakeRepo = FakeRepository(loadResults = listOf(...))
        val viewModel = MyViewModel(app, fakeRepo, testDispatcher)

        // Act
        viewModel.doSomething()
        advanceUntilIdle()  // Critical for async!

        // Assert
        assertEquals(expected, viewModel.state.value)
    }
}
```

## Creating a Fake Repository

**Use Fakes, not Mocks** - More maintainable and readable.

### Pattern

```kotlin
class FakeMyRepository(
    private val loadResults: List<Result<Data>> = emptyList()
) {
    private var callCount = 0
    val calls = mutableListOf<Call>()

    suspend fun load(...): Result<Data> {
        calls.add(Call(...))
        return loadResults[callCount++]
    }
}
```

**Why Fakes?**
- ✅ Readable: `FakeRepo(loadResults = listOf(...))` vs Mockito
- ✅ Reusable across tests
- ✅ Self-documenting
- ✅ Easy to debug

See: `app/src/test/java/protect/card_locker/FakeCardRepository.kt` for real example

## Flow Testing with Turbine

For testing StateFlow/SharedFlow emissions.

```kotlin
@Test
fun `test flow emissions`() = runTest {
    viewModel.uiEvents.test {
        viewModel.performAction()

        val event = awaitItem()
        assertTrue(event is UiEvent.Success)

        cancelAndIgnoreRemainingEvents()
    }
}
```

See: `FlowTestTemplate.kt` for more patterns

## Common Pitfalls

### ❌ Forgot `advanceUntilIdle()`

```kotlin
// WRONG - Test will fail or hang
@Test
fun test() = runTest(testDispatcher) {
    viewModel.loadData()
    assertEquals(expected, viewModel.state.value)  // Still Loading!
}

// RIGHT
@Test
fun test() = runTest(testDispatcher) {
    viewModel.loadData()
    advanceUntilIdle()  // Process all coroutines
    assertEquals(expected, viewModel.state.value)  // Now Success!
}
```

### ❌ Missing `InstantTaskExecutorRule`

```kotlin
// WRONG
class MyTest {
    @Test
    fun test() {
        val state = viewModel.state.value  // Error: "No value present"
    }
}

// RIGHT
class MyTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @Test
    fun test() {
        val state = viewModel.state.value  // Works!
    }
}
```

### ❌ Fake Runs Out of Results

```kotlin
// WRONG - Configured only 1 result, called twice
val fake = FakeRepo(loadResults = listOf(Result.success(data)))
fake.load()  // OK
fake.load()  // Error: "No more results configured"

// RIGHT - Configure all expected calls
val fake = FakeRepo(
    loadResults = listOf(
        Result.success(data1),  // First call
        Result.success(data2)   // Second call
    )
)
```

## Best Practices

### DO ✅

- **Test business logic in ViewModel tests** - Fast, no Robolectric
- **Use Fakes for dependencies** - More maintainable
- **Inject test dispatcher** - Enables `advanceUntilIdle()`
- **Call `advanceUntilIdle()` after every async operation**
- **Name tests clearly** - `` `should update state when user saves card` ``
- **Copy templates** - Don't write from scratch

### DON'T ❌

- **Don't test Android framework** - Trust `TextView.setText()` works
- **Don't use `Thread.sleep()`** - Use `advanceUntilIdle()`
- **Don't test private methods** - Test public behavior
- **Don't use `UnconfinedTestDispatcher` by default** - Less predictable
- **Don't forget `@OptIn(ExperimentalCoroutinesApi::class)`**

## Running Tests

```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests "*.MyViewModelTest"

# With detailed output
./gradlew test --info

# Clean + test
./gradlew clean test
```

## Next Steps

- See [Flow Recording Guide](FLOW_RECORDING.md) for recording real user flows
- Check existing tests in `app/src/test/java/protect/card_locker/viewmodels/` for examples
- Browse templates in `app/src/test/java/protect/card_locker/templates/`

## Questions?

- Check template inline comments for detailed explanations
- See real examples: `LoyaltyCardEditActivityViewModelTest.kt`
- Read [Kotlin Coroutines Testing](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/)
- Read [Turbine docs](https://github.com/cashapp/turbine)
