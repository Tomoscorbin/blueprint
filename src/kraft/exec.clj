(ns kraft.exec
  (:require [clojure.java.io :as io]
            [selmer.parser :as selmer]))

(def dir-ids #{:src})

(def template-path
  {:readme          "templates/readme.md.selmer"
   :gitignore       "templates/gitignore.selmer"
   :conftest        "templates/conftest.py.selmer"
   :github-ci       "templates/ci/github/ci.yml.selmer"
   :azure-ci        "templates/ci/azure/ci.yml.selmer"
   :pyproject-lib   "templates/pyproject/lib.toml.selmer"
   :pyproject-dab   "templates/pyproject/dabs.toml.selmer"
   :databricks-yml  "templates/dabs/databricks.yml.selmer"
   :python-version  "templates/python-version.selmer"})

(defn- ensure-parent! [^java.io.File f]
  (when-let [p (.getParentFile f)]
    (.mkdirs p)))

(defn- mkdir! [path]
  (.mkdirs (io/file path)))

(defn- render-template! [path tpl-id data]
  (let [res (get template-path tpl-id)]
    (when-not res
      (throw (ex-info "No template resource for id" {:template-id tpl-id :path path})))
    (let [out (io/file path)]
      (ensure-parent! out)
      (spit out (selmer/render (slurp (io/resource res)) data)))))

(defn execute!
  "Side-effecting: given a qualified layout {id -> absolute-path} and a data map
   (e.g., {:project_name \"demo\" :pkg \"demo\"}), create dirs and render files."
  [layout data]
  ;; 1) make directories first
  (doseq [id dir-ids
          :let [p (get layout id)]
          :when p]
    (mkdir! p))
  ;; 2) render files
  (doseq [[id path] layout
          :when (and (not (dir-ids id)) path)]
    (render-template! path id data)))
