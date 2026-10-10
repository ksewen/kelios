# KELIOS — Instructions for Coding Agents

Read these files in order:

1. `ARCHITECTURE.md` — technical boundaries, architecture-first gate, independent Docker images and runtime branding.
2. `DOMAIN.md` — approved business model and invariants.
3. `CODEX_IMPLEMENTATION_TASK.md` — phased execution and acceptance criteria.

Backend build is **Apache Maven 3**, using `pom.xml` and the committed Maven Wrapper (`./mvnw`); **do not use Gradle**. Backend Docker builder must use Maven 3, while the runtime image must not contain Maven.

Follow the approved specs. Do not invent missing business rules or expand deferred features. **Begin with Phase 0 only.** All project specifications and agent-facing documentation must be in English.

## Git Policy

- All work must remain within the KELIOS repository.
- Git read-only commands are allowed.
- Do not create commits unless explicitly requested.
- Never push to remote repositories without explicit approval.
- Never use destructive Git commands without explicit approval.
- Do not modify files outside the repository.