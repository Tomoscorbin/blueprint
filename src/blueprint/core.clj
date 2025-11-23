(ns blueprint.core
  "Top-level orchestration for the blueprint CLI.

  This namespace wires together:
  - TUI prompts (`blueprint.tui.core`) to collect answers
  - Layout planning (`blueprint.plan`)
  - File rendering (`blueprint.exec`)

  Public entry point:
  - `-main` – dispatch CLI commands (`init`, `--help`/`-h`), prompting and
    generating the project during `init`."
  (:gen-class)
  (:require
   [blueprint.plan :as plan]
   [blueprint.exec :as exec]
   [blueprint.tui.core :as tui]))

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
  (println "  bp --help      # show this help"))

(defn -main
  "CLI entry point for blueprint.

  Commands:
  - `bp init`       → prompt for project config and generate the project
  - `bp --help/-h`  → show usage
  - default/none    → show usage and exit non-zero"
  [& args]
  (let [[cmd & _] args]
    (case cmd
      "init"
      (-> (tui/prompt-answers!)
          (generate-project!))

      "--help"
      (do (print-usage)
          (System/exit 0))

      "-h"
      (do (print-usage)
          (System/exit 0))

      (do
        (when cmd
          (println "Unknown command:" cmd)
          (println))
        (print-usage)
        (System/exit 1)))))
