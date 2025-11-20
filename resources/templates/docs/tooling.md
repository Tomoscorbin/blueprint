# Tooling

## Python packaging

This repo is set up as an installable Python package using a `pyproject.toml` configuration and Hatchling as the build backend
and a `src/`-based layout. That means:

- your code lives under `src/{{ project_name }}/`
- the project can be built into a wheel / sdist
- you can run it as a CLI or as a library
- tools like `uv`, `pytest`, and `mypy` all read from the same configuration file

### Project metadata and runtime dependencies

The `[project]` section in `pyproject.toml` declares the core package metadata. The important fields are:

- `name` – the package name (and import name) for this repo. By default it is whatever you typed at `bp init`.
- `version` – starts at `0.1.0`. This is the canonical version for the package and is also tracked by Commitizen.
- `requires-python` – the supported Python range, pre-populated from the runtime you chose when creating the project
  (for example `>=3.12`).
- `dependencies` – the list of runtime dependencies needed by your actual application code. This is intentionally
  empty in a fresh repo.

When you add a new library that your code needs at runtime, you add it to `dependencies`. For example, if you
decide to use Pydantic:

```toml
[project]
name = "{{ project_name }}"
version = "0.1.0"
requires-python = ">=3.12"
dependencies = [
  "pydantic",
]
```

After editing the file, you refresh your environment so `uv` installs the new dependency:

```bash
make init
```

### Source layout and imports

The repo uses a `src/` layout, which is the recommended modern structure for Python packages. Your package code lives under:

```
src/{{ project_name }}/
```

By default you will see:

```
src/{{ project_name }}/
  __init__.py
  main.py
  ...
```

Because `src/` is the import root, you should always import using the package name, never by reaching into `src/` directly.
For example:

```python
from {{ project_name }}.main import main
```

not:

```python
from src.{{ project_name }}.main import main  # ❌ do not do this
```

Tests are set up to work with this layout as well. When you run:

```bash
make test
```

or:

```bash
uv run pytest
```

pytest will discover tests under `tests/` and import your code from `{{ project_name }}` as a proper installed package.

### Dev dependencies and the dev group

Dev-only tools (linters, type checkers, test frameworks, commit tooling, etc.) are declared under [dependency-groups.dev]:

```toml
[dependency-groups]
dev = [
  "pyspark",
  "pytest",
  "ruff",
  "mypy",
  "commitizen",
  "pre-commit",
]
```

This gives you a clean separation:

[project.dependencies] → required at runtime.

[dependency-groups.dev] → required only for development.

When you first generate the project, you install everything with:

```bash
make init
```

That tells `uv` to install both runtime dependencies and the dev group into the virtual environment.
If you later add optional tools that not every contributor needs, consider creating additional groups instead
of stuffing everything into dev, for example:

```toml
[dependency-groups]
dev = [
  "pyspark",
  "pytest",
  "ruff",
  "mypy",
  "commitizen",
  "pre-commit",
]

docs = [
  "mkdocs",
  "mkdocs-material",
]
```

### Tool configuration in pyproject.toml

Several tools read their configuration directly from pyproject.toml so there is a single source of truth.

Ruff is configured under [tool.ruff]:

```toml
[tool.ruff]
line-length = 120
```

At the moment this only sets the line length, but you can add more Ruff settings here as needed.

Commitizen is configured under [tool.commitizen]:

```toml
[tool.commitizen]
name = "cz_conventional_commits"
version = "0.1.0"
version_files = ["pyproject.toml:version"]
update_changelog_on_bump = true
```

This tells Commitizen to:

- use the standard Conventional Commits syntax (feat, fix, etc.)
- treat the version field in [project] as the canonical version
- update pyproject.toml when bumping
- keep the changelog in sync when bumps happen

In practice, that means you write commit messages like `feat: add customer segmentation job` or
`fix: handle empty input file`.
Then when `cz bump` runs (usually via CI on the main branch), it inspects the commit history since the last tag and decides
whether to do a major, minor, or patch bump. It updates the version in `pyproject.toml`, writes changelog entries,
and creates a git tag.

You can add other tool configs here as your project grows. For example:

```toml
[tool.pytest.ini_options]
testpaths = ["tests"]
python_files = ["test_*.py"]
addopts = "-ra"

[tool.mypy]
check_untyped_defs = true
disallow_untyped_defs = true
ignore_missing_imports = true
mypy_path = ["src"]

[tool.coverage.run]
source = ["{{ project_name }}"]
branch = true
```

### CLI entry point

The project exposes a CLI entry point via [project.scripts]:

```toml
[project.scripts]
main = "{{ project_name }}.main:main"
```

This means:

- The package installs a console command called main.
- When you run main, it calls the `main()` function in `src/{{ project_name }}/main.py`

The generated `main.py` contains a stub `main()` function you can edit. For example:

```python
def main() -> None:
    print("Hello from {{ project_name }}")
```

You can run this locally without installing the package globally:

```bash
uv run main
```

or:

```python
uv run python -m {{ project_name }}.main
```

As the project evolves, you can rename the script or add more entry points by updating [project.scripts].
For example, to expose a trainer command:

```toml
[project.scripts]
main = "{{ project_name }}.main:main"
etl = "{{ project_name }}.etl:run"
```

#### Building wheels and sdists

The [build-system] section configures Hatchling as the build backend:

```toml
[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"
```

You very rarely need to touch this. It tells build tools how to turn the project into standard Python artefacts.

To build the wheel, you use:

```bash
uv build
```

This creates a wheel (`.whl`) and a source distribution (`.tar.gz`)

- Declares the package's metadata under [project]: the name defaults to whatever you entered during `bp init`, the version
  starts at `0.1.0`, and the `requires-python` field is pre-populated from the runtime you chose.The main dependencies
  list is intentionally empty so you can add runtime requirements as you build features.
- Tooling-only dependencies sit under [dependency-groups.dev]. Running `make init` (`uv sync --all-groups`
  under the hood) installs the dev toolchain in one go. You’ll see PySpark, pytest, Ruff, mypy, Commitizen,
  and pre-commit listed. Keep this list to things you expect every contributor to need; create additional groups if you
  want optional extras.
- The [project.scripts] entry wires up a console entry point called `main` that points at `{{project_name}}.main:main`.
  That means `uv run main` (or `python -m {{project_name}}.main`) will execute the generated `src/{{project_name}}/main.py`.
- [build-system] is set to Hatchling, so `uv build` (and CI) produce standard wheel and sdist artifacts without extra configuration.
- Tool-specific configuration also lives in the same file to keep everything centralized: [tool.ruff] applies a project-wide
  line length of 120, and [tool.commitizen] configures the conventional-commit bumping flow (same `0.1.0` version declares
  above, `pyproject.toml` as the version file, changelog auto-updates). The rest of the tooling (Makefile targets, pre-
  commit hooks, CI) points at this file.
