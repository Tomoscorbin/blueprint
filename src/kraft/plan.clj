(ns kraft.plan
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [kraft.policy.python-version :as pyver]))

(def base-layout
  {:main       "src/{pkg}/main.py"
   :readme    "README.md"
   :gitignore ".gitignore"
   :python-version ".python-version"
   :pyproject  "pyproject.toml"
   :conftest   "tests/conftest.py"})

(defn- join [root rel]
  (str (io/file root rel)))

(defn- project->pkg [s]
  (-> s str/trim str/lower-case
      (str/replace #"[^a-z0-9_]+" "_")
      (str/replace #"_+" "_")
      (str/replace #"^_+|_+$" "")))

(defn- apply-pkg
  "Replace {pkg} only in :src."
  [layout project-name]
  (update layout :main #(str/replace % "{pkg}" (project->pkg project-name))))

(defn- qualify-layout
  "Prepend the repo root to every path."
  [layout root]
  (into {} (for [[k path] layout]
             [k (join root path)])))

(defn- choose-ci-layout [{:keys [ci-provider]}]
  (case ci-provider
    :github {:github-ci ".github/workflows/ci.yml"}
    :azure  {:azure-ci "azure-pipelines.yml"}
    {}))

(defn- choose-project-files [{:keys [project-type]}]
  (case project-type
    :dabs       {:databricks-yml "databricks.yml"}
    {}))

(defn- compose-layout [answers]
  (merge base-layout
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
  {:python_version_value (pyver/resolve-python-version answers)
   :requires_python_value (pyver/resolve-requires-python answers)})

