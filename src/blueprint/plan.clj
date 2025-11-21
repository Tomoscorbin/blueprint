(ns blueprint.plan
  "Plan the on-disk layout for a new project."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [blueprint.runtime :as runtime]))
;;TODO: force user to provide valid python package name??
(def ^:private layout-spec
  {:main            {:destination "src/{pkg}/main.py"
                     :source "templates/main.py.selmer"}
   :python-init     {:destination "src/{pkg}/__init__.py"
                     :source nil}
   :runtime         {:destination "src/{pkg}/runtime.py"
                     :source "templates/runtime.py.selmer"}
   :readme          {:destination "README.md"
                     :source "templates/readme.md.selmer"}
   :gitignore       {:destination ".gitignore"
                     :source "templates/gitignore.selmer"}
   :python-version  {:destination ".python-version"
                     :source "templates/python-version.selmer"}
   :pyproject       {:destination "pyproject.toml"
                     :source "templates/pyproject.toml.selmer"}
   :pre-commit      {:destination ".pre-commit-config.yaml"
                     :source "templates/pre-commit-config.yaml.selmer"}
   :conftest        {:destination "tests/conftest.py"
                     :source "templates/conftest.py.selmer"}
   :test-init       {:destination "tests/__init__.py"
                     :source nil}
   :test-main       {:destination "tests/test_main.py"
                     :source "templates/test_main.py.selmer"}
   :makefile        {:destination "Makefile"
                     :source "templates/makefile.selmer"}
   :github-ci       {:destination ".github/workflows/ci.yaml"
                     :source "templates/ci/github/ci.yaml.selmer"}
   :github-bump     {:destination ".github/workflows/bump.yaml"
                     :source "templates/ci/github/bump.yaml.selmer"}
   :azure-ci        {:destination ".azure/ci.yaml"
                     :source "templates/ci/azure/ci.yaml.selmer"}
   :azure-bump      {:destination ".azure/bump.yaml"
                     :source "templates/ci/azure/bump.yaml.selmer"}
   :databricks-yaml  {:destination "databricks.yaml"
                      :source "templates/databricks.yaml.selmer"}
   :sample-job      {:destination "resources/sample_job.job.yaml"
                     :source "templates/sample_job.job.yaml.selmer"}
   :github-ci-md    {:destination "docs/ci.md"
                     :source "templates/docs/github_ci.md.selmer"}
   :azure-ci-md     {:destination "docs/ci.md"
                     :source "templates/docs/azure_ci.md.selmer"}
   :tooling-md      {:destination "docs/tooling.md"
                     :source "templates/docs/tooling.md.selmer"}
   :github-versioning-md   {:destination "docs/versioning.md"
                            :source "templates/docs/github_versioning.md.selmer"}
   :azure-versioning-md    {:destination "docs/version.md"
                            :source "templates/docs/azure_versioning.md.selmer"}})

(def ^:private base-layout-keys
  "Layout entries that are always created, regardless of CI provider or project type."
  [:main
   :python-init
   :runtime
   :readme
   :gitignore
   :python-version
   :pyproject
   :pre-commit
   :conftest
   :test-init
   :test-main
   :makefile
   :tooling-md])

(defn- base-layout
  "Return the subset of `layout-spec` that is common to all projects."
  []
  (select-keys layout-spec base-layout-keys))

(defn- join-path
  "Join `root` and a relative path into a path string."
  [root relative]
  (str (io/file root relative)))

(defn- apply-pkg-placeholder
  "Replace `{pkg}` placeholders in `:destination` for every entry in `layout`.

  `pkg` is expected to be a valid Python package name string."
  [layout pkg]
  (into {}
        (map (fn [[id spec]]
               [id (update spec :destination
                           #(str/replace % "{pkg}" pkg))]))
        layout))

(defn- qualify-layout
  "Prepend the repo root to every `:destination` path in the layout."
  [layout root]
  (into {}
        (map (fn [[id spec]]
               [id (update spec :destination #(join-path root %))]))
        layout))

(defn- choose-ci-layout
  "Return the CI-specific layout entries based on `:ci-provider` in `answers`.

  Recognised providers:
  - :github -> GitHub Actions workflows
  - :azure  -> Azure Pipelines YAML"
  [{:keys [ci-provider]}]
  (case ci-provider
    :github (select-keys layout-spec [:github-ci :github-bump :github-versioning-md :github-ci-md])
    :azure  (select-keys layout-spec [:azure-ci :azure-bump :azure-versioning-md :azure-ci-md])))

(defn- choose-project-files
  "Return additional layout entries based on `:project-type` in `answers`.

  Allowed poject types:
  - :dabs -> Databricks bundle files (databricks.yaml + sample job)"
  [{:keys [project-type]}]
  (case project-type
    :dabs (select-keys layout-spec [:databricks-yaml :sample-job])
    {}))

(select-keys layout-spec [])

(defn- compose-layout
  "Build the full layout specification (still with relative paths and `{pkg}`).

  Combines:
  - the base layout
  - CI-specific files (if any)
  - project-type-specific files (if any)."
  [answers]
  (merge (base-layout)
         (choose-ci-layout answers)
         (choose-project-files answers)))

(defn plan-layout
  "Plan the file layout for a new project."
  [answers]
  (let [root (:project-name answers)]
    (-> (compose-layout answers)
        (apply-pkg-placeholder root)
        (qualify-layout root))))

(defn- collect-additional-details
  "Derived properties shared across templates."
  [answers]
  {:python_version_value (runtime/resolve-python-version answers)
   :requires_python_value (runtime/resolve-requires-python answers)
   :databricks_runtime (runtime/resolve-databricks-runtime answers)})

(defn template-data
  "Build the full Selmer template data map from the user's answers.

  Combines:
  - the raw answers map
  - normalised keys expected by templates
  - derived runtime-related values."
  [answers]
  (merge answers
         {:project_name    (:project-name answers)
          :ci_provider     (:ci-provider answers)
          :project_type    (:project-type answers)
          :databricks_host (:hostname answers)}
         (collect-additional-details answers)))
