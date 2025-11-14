(ns kraft.policy.python-version
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def ^:private databricks-runtime-resource "databricks_runtime.edn")
(def ^:private python-lib-version "3.14")

(defn- load-runtime-catalog [] ;;TODO: rename catalog to manigest?
  (if-some [res (io/resource databricks-runtime-resource)]
    (-> res slurp edn/read-string)
    (throw (ex-info "Unable to load Databricks runtime catalog."
                    {:resource databricks-runtime-resource}))))

(defn- latest-runtime []
  (let [{:keys [latest-lts runtimes] :as catalog} (load-runtime-catalog)]
    (when-not latest-lts
      (throw (ex-info "Databricks runtime catalog missing :latest-lts." {:catalog catalog})))
    (if-some [runtime (get runtimes latest-lts)]
      runtime
      (throw (ex-info "Databricks runtime catalog missing entry for latest-lts."
                      {:latest-lts latest-lts
                       :catalog    catalog})))))

(defn- latest-runtime-python-version []
  (if-some [version (:python (latest-runtime))]
    version
    (throw (ex-info "Latest Databricks runtime is missing the :python key."
                    {}))))

(defn- requires-python-for-runtime
  "Extract :requires-python if present, else compute from raw :python version."
  []
  (if-some [rp (:requires-python (latest-runtime))]
    rp
    (throw (ex-info "Latest Databricks runtime is missing the :requires-python key."
                    {}))))

(defn resolve-python-version
  "Return the python version that should be used for the provided project answers.
   Python libraries use the pinned python-lib version; everything else should track
   the python version from the latest Databricks runtime."
  [{:keys [project-type]}]
  (if (= :python-lib project-type)
    python-lib-version
    (latest-runtime-python-version)))

(defn resolve-requires-python
  "Return the requires-python spec that should be used for the provided project answers.
   Python libraries use a spec based on the pinned python-lib version; everything else
   should track the requires-python from the latest Databricks runtime."
  [{:keys [project-type]}]
  (if (= :dabs project-type)
    (requires-python-for-runtime)))
