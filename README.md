
<website href="https://unit-editor.shopping-now.net/">Visit Website</website>
<github href="https://github.com/airetaM/UnitEditor">View on GitHub</github>

<highlight title="Personal Note" shadow>
A practical tool born from playing OpenRA mods — I wanted a way to inspect and compare unit stats across different mods without juggling raw YAML files. It's built with <skill>Spring Boot</skill>, <skill>Thymeleaf</skill>, <skill>PostgreSQL</skill>, and <skill>Docker</skill>, with a custom YAML parser that resolves OpenRA's inheritance chain to produce clean unit and weapon comparisons.
</highlight>

A web-based tool for uploading, comparing, and analyzing OpenRA game unit definitions.
Load YAML rule files from OpenRA mods to inspect unit stats, weapon profiles, and
damage calculations — organized by named profiles for side-by-side comparisons.

<img src="UnitEditor/preview.jpg" width="800"/>

<!-- <img src="UnitEditor/unit_creation.jpg" width="800"/> -->

<!-- Preview: -->
<!-- <webm src="UnitEditor/preview.mp4" max-width="600" /> -->

### Highlights

- Built with <skill>Spring Boot</skill>, <skill>Thymeleaf</skill>, <skill>PostgreSQL</skill>, and <skill>Docker</skill>
- Parse OpenRA YAML rule files with full inheritance resolution
- Compare units across health, cost, speed, armor type, and damage
- Weapon stats including reload delay, range, burst, and versus armor modifiers
- Profile-based organization for comparing different mods or game versions

### Authoring Conventions

This project processes OpenRA-style YAML unit definitions. Use these tags when
contributing documentation or content:

<highlight title="Inheritance support" shadow>
Units can extend a parent definition via `^` prefix keys (e.g. `^Tank`).
The parser resolves inherited traits before building the Unit or
Weapon entity.
</highlight>

| Tag | Usage |
|-----|-------|
| `<highlight title="..." shadow>` | Callout box for notes or context |
| `<skill>Name</skill>` | Inline technology badge |
| `<website href="...">` | External link button |
| `<github href="...">` | GitHub link button |
| `<download href="...">` | Download link button |
