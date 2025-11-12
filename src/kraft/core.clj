(ns kraft.core 
  (:require [kraft.plan :as plan]
            [kraft.exec :as exec]
            [kraft.tui.core :as tui]))

(def answers {:project-name "demo"
              :ci-provider  :github
              :project-type :python-lib})

;; (defn -main [& _argv]
;;   (let [layout (plan/plan-layout answers)
;;         data   (plan/prepare-template-data answers)]
;;     (exec/execute! layout data)))


(defn -main [& _]
  (tui/ask-project-name!)
  (tui/choose-ci-provider!))
