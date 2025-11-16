(ns kraft.plan
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [kraft.runtime :as runtime]))

(def ^:private layout-spec
  {:main            {:destination "src/{pkg}/main.py"
                     :source "templates/main.py.selmer"}
   :python-init     {:destination "src/{pkg}/__init__.py"
                     :source nil}
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
                     :source "templates/ci/bump.yml.selmer"}
   :azure-ci        {:destination "azure-pipelines.yml"
                     :source "templates/ci/azure/ci.yml.selmer"}
   :databricks-yml  {:destination "databricks.yml"
                     :source "templates/databricks.yml.selmer"}
   :sample-job      {:destination "resources/sample_job.job.yml"
                     :source "templates/sample_job.job.yml.selmer"}})

(def ^:private base-layout-keys
  [:main
   :python-init
   :readme
   :gitignore
   :python-version
   :pyproject
   :pre-commit
   :conftest
   :test-init
   :test-main
   :makefile])

(defn- base-layout []
  (select-keys layout-spec base-layout-keys))

(defn- join [root rel]
  (str (io/file root rel)))

(defn- project->pkg [s]
  (-> s
      str/trim
      str/lower-case
      (str/replace #"[^a-z0-9_]+" "_")
      (str/replace #"_+" "_")
      (str/replace #"^_+|_+$" "")))

(defn- apply-pkg
  "Replace {pkg} placeholders in :destination for every entry in the layout."
  [layout project-name]
  (let [pkg (project->pkg project-name)]
    (into {}
          (map (fn [[k spec]]
                 [k (update spec :destination
                            #(str/replace % "{pkg}" pkg))]))
          layout)))

(defn- qualify-layout
  "Prepend the repo root to every :destination path."
  [layout root]
  (into {}
        (map (fn [[k spec]]
               [k (update spec :destination #(join root %))]))
        layout))

(defn- choose-ci-layout [{:keys [ci-provider]}]
  (case ci-provider
    :github (select-keys layout-spec [:github-ci :github-bump])
    :azure  (select-keys layout-spec [:azure-ci])
    {}))

(defn- choose-project-files [{:keys [project-type]}]
  (case project-type
    :dabs (select-keys layout-spec [:databricks-yml :sample-job])
    {}))

(defn- compose-layout [answers]
  (merge (base-layout)
         (choose-ci-layout answers)
         (choose-project-files answers)))

(defn plan-layout
  "answers -> fully qualified layout map (ids -> absolute/OS-correct paths)."
  [answers]
  (let [root (:project-name answers)]
    (-> (compose-layout answers)
        (apply-pkg root)
        (qualify-layout root))))

(defn collect-additional-details
  "Derived properties shared across templates."
  [answers]
  {:python_version_value (runtime/resolve-python-version answers)
   :requires_python_value (runtime/resolve-requires-python answers)
   :databricks_runtime (runtime/resolve-databricks-runtime answers)})

