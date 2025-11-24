(ns blueprint.tui.core
  "High-level TUI questions and choices used by the CLI.

  This namespace delegates:
  - styling and line-rewriting to `blueprint.tui.terminal`
  - menu rendering to `blueprint.tui.menu`."
  (:require
   [blueprint.tui.menu :as menu]
   [blueprint.tui.terminal :as term]))

(defn- ask-question!
  "Prompt the user with `question` and return their answer as a string."
  [question]
  (print question) (flush)
  (let [answer (read-line)]
    (term/rewrite-prev-line!
      (str question (term/style-answer answer)))
    answer))

(defn- ask-choice!
  "Prompt the user with `question` and an inline menu of `options`,
   then return the selected option key.

   `options` is a sequence of [key label] pairs."
  [question options]
  (print question) (flush)
  (let [options-map (into {} options)
        answer      (menu/create-menu! options)
        label       (get options-map answer)]
    (term/rewrite-prev-line!
      (str question " "
           (term/style-answer (or label (name answer)))))
    answer))

(defn ask-project-name!
  "Ask the user for the project name and return it as a string."
  []
  (ask-question! "What is your project's name? "))

(defn ask-databricks-host!
  "Ask the user for the Databricks host name and return it as a string."
  []
  (ask-question! "Enter your Databricks host name: "))

(defn choose-ci-provider!
  "Ask the user to choose a CI provider and return the selected keyword."
  []
  (ask-choice! "Choose your CI provider:"
               [[:github "GitHub"]
                [:azure  "Azure DevOps"]]))

(defn choose-project-type!
  "Ask the user to choose the project type and return the selected keyword."
  []
  (ask-choice! "Choose your project type:"
               [[:python-lib "Python Library"]
                [:dabs       "Databricks Asset Bundle"]]))
