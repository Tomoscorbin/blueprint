(ns kraft.core 
  (:require [kraft.plan :as plan]
           [kraft.exec :as exec]))

(def answers {:project-name "demo"
              :ci-provider  :github
              :project-type :python-lib})

(defn -main [& _argv]
  (let [layout (plan/plan-layout answers)
        data   (plan/prepare-template-data answers)]
    (exec/execute! layout data)))
