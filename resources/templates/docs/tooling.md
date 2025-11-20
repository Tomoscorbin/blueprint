# Tooling

[comment]: # TODO: talk about .python-version

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

## Commit messages (Commitizen & Conventional Commits)

This repo uses **Commitizen** to standardise commit messages and drive automatic version bumps from git history.
The configuration lives in `pyproject.toml` under `[tool.commitizen]`, and the `commitizen` package is installed
as part of the `dev` dependency group.

At a high level:

- you write commits using the [**Conventional Commits**] (<https://www.conventionalcommits.org/>) format
- Commitizen (and CI) reads those messages
- the version in `pyproject.toml` is bumped based on the types of changes you made

The detailed CI/release flow is described in a separate document; this section focuses on how you should
write commits day to day.

### Commit format

Commit messages should follow the Conventional Commits pattern:

```text
<type>[:optional scope]: <short description>

[optional body]

[optional footer(s)]
```

Common prefixes you are expected to use:

- `feat`: a new feature
- `fix`: a bug fix
- `docs`: documentation-only changes
- `refactor`: code changes that are not features or bug fixes
- `style`: formatting / stylistic changes only
- `test`: adding or updating tests
- `chore`: repo maintenance, tooling changes, etc.

Examples:

```
feat: add segmentation pipeline
fix: handle empty input file 
```

There is also a pre-commit configuration in this repo which can enforce the commit format for you.
See the Pre-commit section for details.

## Pre-commit hooks

This repo uses **pre-commit** to run a set of checks automatically when you make a commit.
The goal is to catch formatting issues, obvious mistakes, and bad commit messages before they reach CI.

The configuration lives in `.pre-commit-config.yaml` at the repo root, and `pre-commit` itself is installed
as part of the `dev` dependency group in `pyproject.toml`.

### Enabling the hooks

After generating or cloning the repo, you need to install the hooks once:

```bash
make init   # install all dev dependencies with uv
make hook   # register the pre-commit hooks
```

After that, the hooks will run automatically when you commit.

On commit, pre-commit will:

- run Ruff for linting/formatting
- run mypy for basic type checking
- run a handful of hygiene checks (YAML/TOML validity, trailing whitespace, merge conflicts, EOF)
- run a Commitizen commit-msg hook to enforce the Conventional Commits format and block direct commits to main

If any hook fails, the commit is rejected. You'll need fix the reported issues and try again. The configuration for
tools like Ruff and mypy lives in `pyproject.toml` under the corresponding [tool.*] sections.

{% if project_type = :dabs %}

## Databricks Asset Bundles (DABs)

This repo comes with a minimal Databricks Asset Bundle setup The bundle is set up to deploy your code to Databricks as
a **Python wheel** rather than as loose files. The reasons for this are:

- **Better dependency management**
  The exact same dependencies you work with locally are imported into the Databricks cluster. This keeps your
  local development environment in sync with Databricks, whilst also ensuring consistency across clusters and workspaces.
- **Clean imports**
  Because your code is an installed package, everything imports via `import {{ project_name }}...`.
  You do not need `sys.path` hacks to import modules.
- **Tracability**
  Each build produces a wheel with a specific version, so you know exactly which code version a given job is running,
  and you can roll forward/back by changing the wheel version.
- **Build-time failures**
  Building a wheel as part of CI can catch issues before they are deployed to Databricks. For example if the wheel
  fails to build due to packaging or dependencies problems.
- **Same code everywhere** – the code you run locally is the same code that gets shipped to Databricks. There is no
  "workspace copy" which can be edited and drift out of sync.

### Bundle configuration

The two main pieces are:

- `databricks.yaml` – top-level bundle configuration
- `resources/sample_job.yml` – a sample job that runs your package as a wheel

These are just starting points. You'll need to reconfigure them to match your own workspaces, clusters, jobs, etc.

`databricks.yaml` defines the bundle, what gets built, and where it gets deployed:

```yaml
bundle:
  name: {{ project_name }}

include:
  - resources/*.yml
  - resources/*/*.yml

artifacts:
  python_artifact:
    type: whl
    build: uv build --wheel
```

The python_artifact section tells Databricks to build a wheel using `uv build --wheel`, which reads from your
`pyproject.toml` and writes into `dist/`.That ties directly into the packaging setup described above. The same version
and metadata you maintain in `pyproject.toml` are what Databricks sees in the built wheel.

The file also defines two targets:

```yaml

targets:
  dev:
    mode: development
    default: true
    workspace:
      host: {{ hostname }}
    presets:
      artifacts_dynamic_version: true

  prod:
    mode: production
    workspace:
      host: {{ hostname }}
      root_path: /Workspace/Users/${workspace.current_user.userName}/.bundle/${bundle.name}/${bundle.target}

```

- `dev` is the default target, using `mode: development` so deployed resources are treated as a development copy.
- `prod` uses `mode: production` and a different `root_path` for where bundle assets live.

The `host` values are templated from what you entered during `bp init`. In a real project you will usually
point `dev.workspace.host` at your development workspace, and point `prod.workspace.host`` at your production
(or at least non-dev) workspace. Adjust`root_path` to match whatever folder structure your team uses
(for example under `/Workspace/Shared/<team>/<project>`).

### Sample job

The repo includes a single example job definition under `resources/sample_job.yml`:

```yaml
resources:
  jobs:
    sample_job:
      name: sample_job
      tasks:
        - task_key: python_wheel_task
          python_wheel_task:
            package_name: {{ project_name }}
            entry_point: main
          job_cluster_key: job_cluster
          libraries:
            - whl: ../dist/*.whl

      job_clusters:
        - job_cluster_key: job_cluster
          new_cluster:
            spark_version: {{ databricks_runtime }}.x-scala2.12
            node_type_id: i3.xlarge
            data_security_mode: SINGLE_USER
            autoscale:
              min_workers: 1
              max_workers: 4
```

This is wired to run your package as a Python wheel task.

Databricks does not call individual `.py` files directly in this setup. Instead, it calls **entry points** defined in
`pyproject.toml` under `[project.scripts]`.

Each entry point is just:

- a name (the console command)
- a dotted path to `module:function` inside `src/{{ project_name }}/`

For example:

```toml
[project.scripts]
main       = "{{ project_name }}.main:main"
etl-daily  = "{{ project_name }}.pipelines.daily:run"
etl-backfill = "{{ project_name }}.pipelines.backfill:run"
```

In your bundle job YAML, you reference these names via `python_wheel_task.entry_point`:

```yaml
resources:
  jobs:
    customer_pipelines:
      name: customer_pipelines
      tasks:
        - task_key: daily
          python_wheel_task:
            package_name: {{ project_name }}
            entry_point: etl-daily
          job_cluster_key: job_cluster
          libraries:
            - whl: ../dist/*.whl

        - task_key: backfill
          python_wheel_task:
            package_name: {{ project_name }}
            entry_point: etl-backfill
          job_cluster_key: job_cluster
          libraries:
            - whl: ../dist/*.whl
```

The pattern is:

- put your pipeline orchestration code in normal modules under src/{{ project_name }}/...
- add a small run()/main()-style function as the entry point
- expose that function in [project.scripts]
- point python_wheel_task.entry_point at that script name
{% endif %}
