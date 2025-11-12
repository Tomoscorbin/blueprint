(ns kraft.tui.core
  (:require [clojure.string :as str]
            [kraft.tui.menu :as menu]))

(def ^:private esc "\u001b[")
(defn- bold-green [s] (str esc "1;32m" s esc "0m")) ; SGR: bold + green
(defn- rewrite-prev-line! [s]
  ;; up 1 line, CR, clear whole line, print s, newline
  (print (str esc "1A" "\r" esc "2K" s "\n"))
  (flush))

(defn ask-question!
  "Ask Q; echo the answer on the same (previous) line in bold green.
   Returns the trimmed answer."
  [question]
  (print question) (flush)
  (let [answer (read-line)]
    (rewrite-prev-line! (str question  (bold-green answer)))
    answer))

(defn ask-project-name! []
  (ask-question! "What is your project's name? "))

(defn choose-ci-provider! []
  (menu/choose! "Choose CI provider:" [[:github "GitHub"] [:azure "Azure DevOps"]]))
