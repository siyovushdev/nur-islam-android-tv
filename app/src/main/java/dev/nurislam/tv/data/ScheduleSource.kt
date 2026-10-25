package dev.nurislam.tv.data

private val TestSourceLabel = Regex(
    """(?:^|[^\p{L}\p{N}])(?:test[\p{L}\p{N}]*|synthetic[\p{L}\p{N}]*|demo[\p{L}\p{N}]*|тест[\p{L}\p{N}]*|демо[\p{L}\p{N}]*|макет[\p{L}\p{N}]*)(?=$|[^\p{L}\p{N}])""",
    RegexOption.IGNORE_CASE,
)
fun isTestSource(source: String): Boolean = TestSourceLabel.containsMatchIn(source)

