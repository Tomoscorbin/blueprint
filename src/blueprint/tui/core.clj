(ns blueprint.tui.core
  "High-level TUI questions and choices used by the CLI.

  This namespace delegates:
  - styling and line-rewriting to `blueprint.tui.terminal`
  - menu rendering to `blueprint.tui.menu`."
  (:require
   [blueprint.tui.menu :as menu]
   [blueprint.tui.terminal :as term]))

;; --- Internal helpers ------------------------------------------------------

(defn- prompt!
  "Print `question` as a prompt and flush, without a trailing newline.

  The `question` string is reused when rewriting the line after the user
  answers, so callers are responsible for including any desired trailing
  space (e.g. \"Name: \")."
  [question]
  (print question)
  (flush))

(defn- ask-question!
  "Prompt the user with `question` and return their answer as a string.

  The question is printed exactly as given (no newline), the user types
  their answer, and then the previous line is rewritten to show the
  question plus a styled answer."
  [question]
  (prompt! question)
  (let [answer        (read-line)
        styled-answer (term/style-answer answer)]
    (term/rewrite-prev-line!
      (str question styled-answer))
    answer))

(defn- ask-choice!
  "Prompt the user with `question` and an inline menu of `options`,
   then return the selected option key.

   `options` is a sequence of [key label] pairs.
   The `question` is printed once as a prompt, the menu is rendered below
   it, and after a selection is made the prompt line is rewritten to show
   the question and the chosen label (or the key name if no label is
   found)."
  [question options]
  (prompt! question)
  (let [options-map  (into {} options)
        selected-key (menu/create-menu! options)
        label        (get options-map selected-key)
        display      (or label (name selected-key))]
    (term/rewrite-prev-line!
      (str question " " (term/style-answer display)))
    selected-key))

;; --- Public API ------------------------------------------------------------

(defn ask-project-name!
  "Ask the user for the project name and return it as a string."
  []
  (ask-question! "What is your project's name? "))

(defn ask-databricks-host!
  "Ask the user for the Databricks host name and return it as a string."
  []
  (ask-question! "Enter your Databricks host name: "))

(defn choose-ci-provider!
  "Ask the user to choose a CI provider and return the selected keyword.

  Returns one of:
  - :github
  - :azure"
  []
  (ask-choice! "Choose your CI provider:"
               [[:github "GitHub"]
                [:azure  "Azure DevOps"]]))

(defn choose-project-type!
  "Ask the user to choose the project type and return the selected keyword.

  Returns one of:
  - :python-lib
  - :dabs"
  []
  (ask-choice! "Choose your project type:"
               [[:python-lib "Python Library"]
                [:dabs       "Databricks Asset Bundle"]]))
