package dev.clankyard.ai.tools

internal val READ_FILE_SCHEMA = """
{
  "type": "object",
  "required": ["path"],
  "properties": {
    "path": { "type": "string" }
  }
}
""".trimIndent()

internal val LIST_DIRECTORY_SCHEMA = """
{
  "type": "object",
  "properties": {
    "path": { "type": "string" }
  }
}
""".trimIndent()

internal val SEARCH_TEXT_SCHEMA = """
{
  "type": "object",
  "required": ["query"],
  "properties": {
    "query": { "type": "string" },
    "path": { "type": "string" }
  }
}
""".trimIndent()

internal val GIT_STATUS_SCHEMA = """
{
  "type": "object",
  "properties": {}
}
""".trimIndent()

internal val GIT_DIFF_SCHEMA = """
{
  "type": "object",
  "properties": {
    "path": { "type": "string" }
  }
}
""".trimIndent()

internal val CREATE_FILE_SCHEMA = """
{
  "type": "object",
  "required": ["path", "content"],
  "properties": {
    "path": { "type": "string" },
    "content": { "type": "string" }
  }
}
""".trimIndent()

internal val REPLACE_TEXT_SCHEMA = """
{
  "type": "object",
  "required": ["path", "old_string", "new_string"],
  "properties": {
    "path": { "type": "string" },
    "old_string": { "type": "string" },
    "new_string": { "type": "string" }
  }
}
""".trimIndent()

internal val RENAME_FILE_SCHEMA = """
{
  "type": "object",
  "required": ["from", "to"],
  "properties": {
    "from": { "type": "string" },
    "to": { "type": "string" }
  }
}
""".trimIndent()
