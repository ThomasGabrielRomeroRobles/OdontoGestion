---
description: Implementar las tareas de un change con TDD estricto
argument-hint: c-NN-nombre
---
Usá la herramienta Skill para invocar la skill `openspec-apply-change` con este argumento: `$ARGUMENTS`.

- El nombre del change va en minúsculas (el CLI de OpenSpec rechaza mayúsculas): si llega `C-04-pacientes-core`, usá `c-04-pacientes-core`.
- Si no se pasó argumento, tomá el change del bloque de la persona según el Protocolo de inicio de CLAUDE.md, o preguntá cuál.
- Respetá el nivel de Governance del change en CHANGES.md y las reglas duras de CLAUDE.md.
