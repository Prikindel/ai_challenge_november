# Kotlin Cheat Sheet

## Основные конструкции

### Переменные
```kotlin
val immutable = "неизменяемая"
var mutable = "изменяемая"
```

### Функции
```kotlin
fun greet(name: String): String {
    return "Привет, $name!"
}

// Однострочная функция
fun add(a: Int, b: Int) = a + b
```

### Классы
```kotlin
data class User(val name: String, val age: Int)

class Service {
    fun doSomething() {
        // ...
    }
}
```

### Null safety
```kotlin
val nullable: String? = null
val length = nullable?.length ?: 0
nullable?.let { println(it) }
```

## Коллекции

### Списки
```kotlin
val list = listOf(1, 2, 3)
val mutableList = mutableListOf(1, 2, 3)
```

### Мапы
```kotlin
val map = mapOf("key" to "value")
val mutableMap = mutableMapOf("key" to "value")
```

### Операции
```kotlin
list.map { it * 2 }
list.filter { it > 1 }
list.find { it > 1 }
list.groupBy { it % 2 }
```

## Корутины

```kotlin
suspend fun fetchData(): String {
    delay(1000)
    return "Data"
}

// Использование
runBlocking {
    val data = fetchData()
}
```

## Расширения

```kotlin
fun String.removeSpaces(): String {
    return this.replace(" ", "")
}

"hello world".removeSpaces() // "helloworld"
```

## Делегаты

```kotlin
class Example {
    val lazyValue: String by lazy {
        "Computed"
    }
}
```

## Sealed классы

```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}
```

