# Blueprint

An opinionated CLI for spinning up best-practice data engineering projects in seconds.

- 🧱 Project scaffold: sets up a full data-engineering repo with sensible defaults for structure, config, and tooling
- 🔀 Bundle or package: spin up either a Databricks Asset Bundle project or a standalone Python package
- 🔁 CI/CD: generates GitHub Actions or Azure Pipelines workflows that run linting, type checking, pytest, wheel builds, and Databricks Asset Bundle validation
- 🛠️ Dev tooling: pyproject.toml prewired for uv, Ruff, mypy, and pytest, plus pre-commit hooks, and a Makefile that wraps everyday commands (lint, test, format, etc.)
- 🔢 Versioning: uses Commitizen to automatically bump the project version and create tags when changes are merged
- ☁️ Databricks: creates a Databricks Asset Bundle configured to build your code as a wheel
- 🔥 Testing: pytest layout with a lightweight Spark session fixture for local unit tests

## Installation

Assumes `uv` is already installed and available on your PATH (see <https://docs.astral.sh/uv/>).

```bash
curl -fsSL https://raw.githubusercontent.com/tomoscorbin/blueprint/main/scripts/install.sh | bash
```

## Usage

```bash
bp init
```

Follow the arrow-key prompts (project name, CI provider, project type, and Databricks host if you pick a bundle).

Then, in the generated repo root:

```bash
make init
make hook
```

`make init` sets up the virtualenv and installs dependencies with `uv`.

`make hook` installs the pre-commit hooks.
