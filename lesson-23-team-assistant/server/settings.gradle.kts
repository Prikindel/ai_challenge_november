rootProject.name = "lesson-23-team-assistant"

include(":mcp-common")
project(":mcp-common").projectDir = file("../mcp-common")

include(":task-mcp-server")
project(":task-mcp-server").projectDir = file("../task-mcp-server")

include(":rag-mcp-server")
project(":rag-mcp-server").projectDir = file("../rag-mcp-server")

include(":git-mcp-server")
project(":git-mcp-server").projectDir = file("../git-mcp-server")

include(":crm-mcp-server")
project(":crm-mcp-server").projectDir = file("../crm-mcp-server")

