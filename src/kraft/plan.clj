(ns kraft.plan
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def base-layout
  {:src       "src/{pkg}"
   ;; :tests     "tests"
   :readme    "README.md"
   :gitignore ".gitignore"
   :conftest  "tests/conftest.py"})

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
  (update layout :src #(str/replace % "{pkg}" (project->pkg project-name))))

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
    :dabs       {:databricks-yml "databricks.yml"
                 :pyproject-dab   "pyproject.toml"}
    :python-lib {:pyproject-lib   "pyproject.toml"}
    {}))

(defn- compose-layout [answers]
  (merge base-layout
         (choose-ci-layout answers)
         (choose-project-files answers)))

(defn plan-layout
  "answers -> fully qualified layout map (ids -> absolute/OS-correct paths)."
  [answers]
  (let [root (:project-name answers)]
    (-> (compose-layout answers)   ; base + CI + project slice (relative)
        (apply-pkg root)           ; \"src/{pkg}\" -> \"src/<slug>\"
        (qualify-layout root))))   ; prepend repo root to every path

(defn prepare-template-data
  "Values your Selmer templates will use in file *contents*."
  [answers]
  {:project_name (:project-name answers)
   :pkg          (project->pkg (:project-name answers))})
