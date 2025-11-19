(ns blueprint.tui.core
  (:require [blueprint.tui.menu :as menu]))

(def ^:private esc "\u001b[")
(defn- bold-green [s] (str esc "1;32m" s esc "0m"))
(defn- rewrite-prev-line!
  "Rewrite the previous terminal line with string `s`.

  Moves the cursor up one line, clears it, prints `s`, then moves to a new line."
  [s]
  (print (str esc "1A" "\r" esc "2K" s "\n"))
  (flush))

(defn- ask-question!
  "Prompt the user with `question` and return their answer as a string."
  [question]
  (print question) (flush)
  (let [answer (read-line)]
    (rewrite-prev-line! (str question  (bold-green answer)))
    answer))

(defn- ask-choice!
  "Prompt the user with `question` and an inline menu of `options`, then return
   the selected option key."
  [question options]
  (print question) (flush)
  (let [opt-map (into {} options)
        answer  (menu/create-menu! options)
        label   (get opt-map answer)]
    (rewrite-prev-line! (str question " " (bold-green (or label (name answer)))))
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
  (ask-choice! "Choose your CI provider:" [[:github "GitHub"] [:azure "Azure DevOps"]]))

(defn choose-project-type!
  "Ask the user to choose the project type and return the selected keyword."
  []
  (ask-choice! "Choose your project type:"
               [[:python-lib "Python Library"]
                [:dabs "Databricks Asset Bundle"]]))
