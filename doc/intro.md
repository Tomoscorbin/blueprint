# Blueprint in a nutshell

Blueprint is an opinionated CLI that scaffolds data-engineering ready Python projects. It asks you a handful of interactive questions, plans a layout, then renders templates into a brand-new repository named after your project.

- Two project types: a plain Python library or a Databricks Asset Bundle (DABs) that ships your code as a wheel and includes a sample job.
- Two CI providers: GitHub Actions or Azure Pipelines. Both run linting, types, tests, and wheel builds; both also ship a Commitizen-driven bump pipeline.
- Modern Python defaults: `pyproject.toml`, `src/` layout, uv for dependencies, Ruff + mypy + pytest + pre-commit, and a Makefile that wraps the common commands.
- Baked-in docs: generated projects include `docs/ci.md`, `docs/versioning.md`, and `docs/tooling.md` explaining how everything is wired.

## Running the CLI

Install the released binary (assumes `uv` is on your PATH):

```bash
curl -fsSL https://raw.githubusercontent.com/tomoscorbin/blueprint/main/scripts/install.sh | bash
bp init
```

You will be prompted for:

1) Project name → becomes the repository directory and package name.
2) CI provider → GitHub Actions or Azure DevOps.
3) Project type → Python library or Databricks Asset Bundle.
4) Databricks hostname → only asked when you pick the bundle option (you can skip by hitting enter).

When the prompts finish, Blueprint creates a sibling directory matching your project name and writes all files there.

## What Blueprint generates

Every project gets:

- `pyproject.toml` configured for uv and Hatchling, a `.python-version`, and `src/<project>/` with a stub `main.py`.
- Dev tooling: Ruff, mypy, pytest (with a SparkSession fixture), pre-commit, Commitizen config, and a Makefile with `sync`, `lint`, `types`, `test`, `coverage`, and `hook`.
- Tests: `tests/test_main.py` plus `tests/conftest.py`.
- Docs: `docs/tooling.md`, CI docs for your provider, and versioning docs for your provider.

CI provider-specific files:

- GitHub Actions → `.github/workflows/ci.yaml` and `.github/workflows/bump.yaml`.
- Azure DevOps → `.azure/ci.yaml` and `.azure/bump.yaml`.

Project-type extras:

- Python library → targets Python 3.14 by default and stops there.
- Databricks Asset Bundle → `databricks.yaml` wired for wheel builds (`uv build --wheel`), a sample job under `resources/sample_job.job.yaml`, and versions lifted from `resources/databricks_runtime.edn` (runtime 17.3 LTS / Python 3.12). The Databricks hostname you enter is injected into the bundle config.

## After generation

Inside the new project directory:

```bash
make sync      # create the uv-managed virtualenv and install deps
git init       # needed so Commitizen + pre-commit have a repo
make hook      # install the commit-msg hook that enforces Conventional Commits
```

Commit and push to your chosen CI provider. The pipelines will run automatically (GitHub Actions on PRs to `main`, Azure Pipelines when you point a pipeline at `.azure/ci.yaml`). The bump pipeline runs on pushes to `main` and updates the version, changelog, and tags.
