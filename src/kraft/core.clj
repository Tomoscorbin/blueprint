(ns kraft.core
  (:require [kraft.plan :as plan]
            [kraft.exec :as exec]
            [kraft.tui.core :as tui]))

(def answers {:project-name "demo"
              :ci-provider  :github
              :project-type :python-lib})

(defn -main [& _]
  (tui/ask-project-name!)
  (tui/choose-ci-provider!)
  (tui/choose-project-type!))
