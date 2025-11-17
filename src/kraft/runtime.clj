(ns kraft.runtime
  "Resolve runtime-related values (Python version, requires-python, Databricks runtime)
  from a manifest EDN and project-type-specific rules."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def ^:private databricks-runtime-resource "databricks_runtime.edn")
(def ^:private python-lib-version "3.14")

(defn- load-runtime-manifest
  "Load the Databricks runtime manifest EDN from the classpath.

  Returns the parsed EDN map.

  Throws an exception if the resource cannot be found."
  []
  (if-some [res (io/resource databricks-runtime-resource)]
    (-> res slurp edn/read-string)
    (throw (ex-info "Unable to load Databricks runtime manifest."
                    {:resource databricks-runtime-resource}))))

(defn- runtime-python-version
  "Return the Python version string from the runtime manifest."
  []
  (:python (load-runtime-manifest)))

(defn- runtime-requires-python
  "Return the 'requires-python' string from the runtime manifest."
  []
  (:requires-python (load-runtime-manifest)))

(defn- runtime-version
  "Return the Databricks runtime version string (for example \"17.3\") from the
  manifest."
  []
  (:version (load-runtime-manifest)))

(defn resolve-python-version
  "Return the Python version string to use for the project.

  For project-type :python-lib, this returns a fixed version from
  `python-lib-version`. For all other project types, it returns the Python
  version from the Databricks runtime manifest."
  [{:keys [project-type]}]
  (if (= :python-lib project-type)
    python-lib-version
    (runtime-python-version)))

(defn resolve-requires-python
  "Return the 'requires-python' string for DABs projects.

  For project-type :dabs, this returns the value from the Databricks runtime
  manifest."
  [{:keys [project-type]}]
  (when (= :dabs project-type)
    (runtime-requires-python)))

(defn resolve-databricks-runtime
  "Return the Databricks runtime version string (for example \"17.3\") for DABs projects."
  [{:keys [project-type]}]
  (when (= :dabs project-type)
    (runtime-version)))
