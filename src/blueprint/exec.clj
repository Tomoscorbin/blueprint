(ns blueprint.exec
  "Execute a planned layout by creating directories and writing files."
  (:require [clojure.java.io :as io]
            [selmer.parser :as selmer]))

(defn- ensure-parent!
  "Ensure that the parent directory of `f` exists, creating it if necessary."
  [^java.io.File f]
  (when-let [p (.getParentFile f)]
    (.mkdirs p)))

(defn- write-empty-file! [destination]
  (let [file (io/file destination)]
    (ensure-parent! file)
    (spit file "")))

(defn- render-template!
  "Render a Selmer template resource into the given destination file."
  [destination template-resource data template-id]
  (let [resource (io/resource template-resource)]
    (when-not resource
      (throw (ex-info "No template resource for id"
                      {:template-id       template-id
                       :template-resource template-resource
                       :destination       destination})))
    (let [file (io/file destination)]
      (ensure-parent! file)
      (spit file (selmer/render (slurp resource) data)))))

(defn execute!
  "Side-effecting: given a qualified layout
   {id -> {:destination abs-path, :source template-or-nil}}
   and a data map (e.g., {:project_name \"demo\"}),
   create dirs and render or touch files."
  [layout data]
  (doseq [[template-id {:keys [destination source]}] layout]
    (if source
      (render-template! destination source data template-id)
      (write-empty-file! destination))))
