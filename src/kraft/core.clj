(ns kraft.core
  (:require
   [kraft.plan :as plan]
   [kraft.exec :as exec]
   [kraft.tui.core :as tui]))

(defn- prompt-answers! []
  (let [project-name (tui/ask-project-name!)
        ci-provider  (tui/choose-ci-provider!)
        project-type (tui/choose-project-type!)]
    (cond-> {:project-name project-name
             :ci-provider  ci-provider
             :project-type project-type}

      ;; Only ask for hostname when project-type is :dabs
      (= project-type :dabs)
      (assoc :hostname (tui/ask-databricks-host!)))))

(defn- ->template-data [answers]
  (merge answers
         {:project_name (:project-name answers)
          :ci_provider  (:ci-provider answers)
          :project_type (:project-type answers)
          :databricks_host (:hostname answers)}
         (plan/collect-additional-details answers))) ;;TODO: I dont like this here

(defn- generate-project! [answers]
  (let [layout (plan/plan-layout answers)
        data   (->template-data answers)]
    (exec/execute! layout data)))

(defn -main [& _]
  (-> (prompt-answers!)
      (generate-project!)))
