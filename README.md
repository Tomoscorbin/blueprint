# Blueprint

![GitHub Release](https://img.shields.io/github/v/release/Tomoscorbin/blueprint)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/Tomoscorbin/blueprint/ci.yaml?label=CI)

An opinionated CLI for spinning up data engineering projects. It takes care of boilerplate setup like static analysis, CI pipelines, and Python packaging, so you are not wiring the same basics together from scratch every time you start a new project.

- 🧱 Project scaffold: sets up a full data-engineering repo with sensible defaults for structure, config, and tooling
- 🔀 Bundle or package: spin up either a Databricks Asset Bundle project or a standalone Python package
- 🔁 CI/CD: generates GitHub Actions or Azure Pipelines workflows that run linting, type checking, pytest, wheel builds,
  and Databricks Asset Bundle validation
- 🛠️ Dev tooling: pyproject.toml prewired for uv, Ruff, mypy, and pytest, plus pre-commit hooks, and a Makefile that
  wraps everyday commands
- 🔢 Versioning: uses Commitizen to automatically bump the project version and create tags when changes are merged
- ☁️ Databricks: creates a Databricks Asset Bundle configured to build your code as a wheel
- 🔥 Testing: pytest layout with a lightweight Spark session fixture for local unit tests
- 📚 Docs: generated projects include markdown guides explaining how packaging,
  CI, versioning, and Databricks bundles are wired

## Requirements

The generated Python projects assume [`uv`](https://docs.astral.sh/uv/) is already installed and available on your `PATH`.

## Installation

### Linux / macOS

Install the `bp` binary with:

```bash
curl -fsSL https://raw.githubusercontent.com/tomoscorbin/blueprint/main/scripts/install.sh | bash

```

This will download the binary and place it in a directory on your PATH
(by default `~/.local/bin` if present, otherwise `/usr/local/bin`). You can override the target directory with BP_BIN_DIR:

```bash
BP_BIN_DIR="$HOME/.local/bin" \
  curl -fsSL https://raw.githubusercontent.com/tomoscorbin/blueprint/main/scripts/install.sh | bash
```

### Windows

From PowerShell:

```Powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
irm https://raw.githubusercontent.com/tomoscorbin/blueprint/main/scripts/install.ps1 | iex
```

This downloads the latest `bp.exe` into a user-local directory (e.g. `%LOCALAPPDATA%\bp`) and prints instructions for
adding that directory to your PATH.

If you prefer to install from Git Bash, you can re-use the shell installer:

```bash
curl -fsSL https://raw.githubusercontent.com/tomoscorbin/blueprint/main/scripts/install.sh | bash
```

This will place `bp.exe` in a user-local directory (by default `$HOME/.local/bin`). The script will print the
corresponding Windows-style path (e.g. `C:\Users\you\.local\bin`). Add that directory to your Windows PATH
so you can run `bp` from PowerShell / Windows Terminal.

## Usage

```bash
bp init
```

Follow the arrow-key prompts (project name, CI provider, project type, and Databricks host if you pick a bundle).

Then, in the generated repo root:

```bash
make sync      # create virtualenv and install dependencies with uv
git init       # initialise a git repository (required for hooks)
make hook      # install pre-commit hooks into .git/hooks
```

Note: `git init` is required before `make hook`, because pre-commit needs an initialised Git repository to install the hooks into.

See [here](doc/intro.md) for more information.
