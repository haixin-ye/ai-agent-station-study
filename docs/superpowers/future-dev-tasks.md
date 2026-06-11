# Future Development Tasks

## AutoAgent Harness

1. Context auto-planning
   - Need an automatic context planning mechanism for long tasks.
   - Example: when publishing a long CSDN article, the prompt may contain too much unrelated content to fit the context window.
   - The runtime should plan, split, summarize, or load only the required artifact/context instead of passing the full prompt state.

2. Subagent scheduling
   - Add subagent dispatch support for specialized work.
   - Candidate use cases include code exploration, code review, RAG evidence checking, test failure diagnosis, and long-form content refinement.

3. Coding agent capability
   - Add code and file operation support as a future capability, likely through file_system / command MCP tools.
   - Keep the first design simple: MainAgentNode decides how to use file/code tools, Runtime handles approval, tool receipt recording, and fact-level verification.
   - Defer full coding-agent architecture, project-wide context planning, code-quality verification, and deep repository understanding to a later design.
   - ASK_USER approval must be available for write, delete, move, overwrite, destructive command, network, install, and risky git operations.
