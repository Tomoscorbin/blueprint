(ns kraft.core
  (:require [clojure.string :as str]
            [kraft.plan :as plan]
            [kraft.exec :as exec]
            [kraft.tui.core :as tui]))

(defn- prompt-answers! []
  (let [project-name (-> (tui/ask-project-name!) (or "") str/trim)
        ci-provider  (tui/choose-ci-provider!)
        project-type (tui/choose-project-type!)]
    {:project-name project-name
     :ci-provider  ci-provider
     :project-type project-type}))

(defn- ->template-data [answers]
  (merge answers
         {:project_name (:project-name answers)
          :ci_provider  (:ci-provider answers)
          :project_type (:project-type answers)}
         (plan/collect-additional-details answers)))

(defn- generate-project! [answers]
  (let [layout (plan/plan-layout answers)
        data   (->template-data answers)]
    (exec/execute! layout data)))

(defn -main [& _]
  (-> (prompt-answers!)
      (generate-project!)))
