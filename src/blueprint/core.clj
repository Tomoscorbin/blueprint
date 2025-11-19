(ns blueprint.core
  "Top-level orchestration for the blueprint CLI.

  This namespace wires together:
  - TUI prompts (`blueprint.tui.core`) to collect answers
  - Layout planning (`blueprint.plan`)
  - File rendering (`blueprint.exec`)

  Public entry point:
  - `-main` – prompt for answers and generate the project on disk."
  (:gen-class)
  (:require
   [blueprint.version :as v]
   [blueprint.plan :as plan]
   [blueprint.exec :as exec]
   [blueprint.tui.core :as tui]))

(defn- prompt-answers!
  "Interactively collect the high-level project configuration from the user.

  Asks, in order:
  - Project name
  - CI provider
  - Project type
  - Databricks host name (only when project type is :dabs)

  Returns:
  - A map with at least:
      {:project-name string
       :ci-provider  keyword
       :project-type keyword}
    and, for :dabs projects, also:
      {:hostname string}"
  []
  (let [project-name (tui/ask-project-name!)
        ci-provider  (tui/choose-ci-provider!)
        project-type (tui/choose-project-type!)]
    (cond-> {:project-name project-name
             :ci-provider  ci-provider
             :project-type project-type}

      ;; Only ask for hostname when project-type is :dabs
      (= project-type :dabs)
      (assoc :hostname (tui/ask-databricks-host!)))))

(defn- generate-project!
  "Given the collected `answers` map, plan and materialise the project on disk.

  Side effects:
  - Writes files and directories under the project root derived from `answers`."
  [answers]
  (let [layout (plan/plan-layout answers)
        data   (plan/template-data answers)]
    (exec/execute! layout data)))

(defn- print-usage []
  (println "bp - blueprint CLI")
  (println)
  (println "Usage:")
  (println "  bp init        # interactively create a new project")
  (println "  bp --version   # show version")
  (println "  bp --help      # show this help"))

(defn- print-version []
  (println "bp" v/version))

(defn -main
  "CLI entry point for blueprint.

  Commands:
  - `bp init`   → prompt for project config and generate the project
  - `bp help`   → show usage
  - `bp`        → show usage and exit non-zero"
  [& args]
  (let [[cmd & _] args]
    (case cmd
      ;; happy path: bp init
      "init"
      (-> (prompt-answers!)
          (generate-project!))

      "--version"
      (do (print-version)
          (System/exit 0))

      "-V"
      (do (print-version)
          (System/exit 0))

      ;; help commands
      "--help"
      (do (print-usage)
          (System/exit 0))

      "-h"
      (do (print-usage)
          (System/exit 0))

      ;; default / no command / unknown command
      (do
        (when cmd
          (println "Unknown command:" cmd)
          (println))
        (print-usage)
        (System/exit 1)))))
