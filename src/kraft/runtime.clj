(ns kraft.runtime
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))
;; TODO: rename this file and move out of policy/
(def ^:private databricks-runtime-resource "databricks_runtime.edn")
(def ^:private python-lib-version "3.14")

(defn- load-runtime-manifest []
  (if-some [res (io/resource databricks-runtime-resource)]
    (-> res slurp edn/read-string)
    (throw (ex-info "Unable to load Databricks runtime manifest."
                    {:resource databricks-runtime-resource}))))

(defn- runtime-python-version []
  (:python (load-runtime-manifest)))

(defn- runtime-requires-python []
  (:requires-python (load-runtime-manifest)))

(defn- runtime-version []
  (:version (load-runtime-manifest)))

(defn resolve-python-version
  [{:keys [project-type]}]
  (if (= :python-lib project-type)
    python-lib-version
    (runtime-python-version)))

(defn resolve-requires-python
  "Only relevant for DABs projects."
  [{:keys [project-type]}]
  (when (= :dabs project-type)
    (runtime-requires-python)))

(defn resolve-databricks-runtime
  "Return the Databricks runtime version string (e.g. \"17.3\") for DABs projects."
  [{:keys [project-type]}]
  (when (= :dabs project-type)
    (runtime-version)))
