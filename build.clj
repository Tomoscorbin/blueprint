(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'io.github.tomoscorbin/blueprint)
(def version "0.3.8")

(def main 'blueprint.core)

(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))

(def uber-file (format "target/%s-%s-standalone.jar"
                       (name lib)
                       version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  ;; clean build dir
  (clean nil)

  ;; copy sources/resources
  (b/copy-dir {:src-dirs  ["src" "resources"]
               :target-dir class-dir})

  ;; compile Clojure to classes
  (b/compile-clj {:basis     basis
                  :src-dirs  ["src"]
                  :class-dir class-dir})

  ;; build the uberjar
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     basis
           :main      main
           :lib       lib})
  (println "Built" uber-file))
