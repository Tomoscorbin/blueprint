(ns kraft.plan
  "Plan the on-disk layout for a new project.

  Public API:
  - `plan-layout`: build the full layout of files to create.
  - `collect-additional-details`: derive extra values shared across templates."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [kraft.runtime :as runtime]))
;;TODO: force user to provide valid python package name??
;; Layout specification
;;
;; `layout-spec` is a map of:
;;   layout-id keyword
;;   -> {:destination \"relative/path/with/{pkg}\"
;;       :source      \"templates/...\" | nil}
;;
;; `:destination` paths are relative to the project root until `qualify-layout`
;; is applied.
;; `:source` is a classpath resource path for the template, or nil for
;; empty files.
;;
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
   :pre-commit      {:destination ".pre-commit-config.yml"
                     :source "templates/pre-commit-config.yml.selmer"}
   :conftest        {:destination "tests/conftest.py"
                     :source "templates/conftest.py.selmer"}
   :test-init       {:destination "tests/__init__.py"
                     :source nil}
   :test-main       {:destination "tests/test_main.py"
                     :source "templates/test_main.py.selmer"}
   :makefile        {:destination "Makefile"
                     :source "templates/makefile.selmer"}
   :github-ci       {:destination ".github/workflows/ci.yml"
                     :source "templates/ci/github/ci.yml.selmer"}
   :github-bump     {:destination ".github/workflows/bump.yml"
                     :source "templates/ci/github/bump.yml.selmer"}
   :azure-ci        {:destination ".azure/ci.yml"
                     :source "templates/ci/azure/ci.yml.selmer"}
   :azure-bump      {:destination ".azure/bump.yml"
                     :source "templates/ci/azure/bump.yml.selmer"}
   :databricks-yml  {:destination "databricks.yml"
                     :source "templates/databricks.yml.selmer"}
   :sample-job      {:destination "resources/sample_job.job.yml"
                     :source "templates/sample_job.job.yml.selmer"}})

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
   :makefile])

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
    :github (select-keys layout-spec [:github-ci :github-bump])
    :azure  (select-keys layout-spec [:azure-ci :azure-bump])))

(defn- choose-project-files
  "Return additional layout entries based on `:project-type` in `answers`.

  Allowed poject types:
  - :dabs -> Databricks bundle files (databricks.yml + sample job)"
  [{:keys [project-type]}]
  (case project-type
    :dabs (select-keys layout-spec [:databricks-yml :sample-job])))

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

(defn collect-additional-details
  "Derived properties shared across templates."
  [answers]
  {:python_version_value (runtime/resolve-python-version answers)
   :requires_python_value (runtime/resolve-requires-python answers)
   :databricks_runtime (runtime/resolve-databricks-runtime answers)})

