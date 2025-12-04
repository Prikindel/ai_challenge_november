class TestClass {
    // BUG: не закрывается ресурс
    fun readFile() {
        val file = File("test.txt")
        val content = file.readText()
        // Нет file.close()
    }

    // SECURITY: SQL injection
    fun query(name: String) {
        val sql = "SELECT * FROM users WHERE name = '$name'"
    }

    // PERFORMANCE: O(n²) сложность
    fun findDuplicates(list: List<String>) {
        for (item in list) {
            for (other in list) {
                if (item == other) { ... }
            }
        }
    }

    // STYLE: слишком длинная функция
    fun longFunction() {
        // 200 строк кода...
    }

    // LOGIC: не обрабатывается пустой список
    fun getFirst(list: List<String>): String {
        return list[0]  // IndexOutOfBoundsException
    }

    // DOCUMENTATION: нет комментариев
    fun undocumentedFunction(x: Int, y: Int): Int {
        return x * y + 10
    }
}