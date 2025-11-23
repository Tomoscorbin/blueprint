(ns blueprint.tui.core
  "Lanterna-based terminal UI for collecting Blueprint project configuration.

  Public entry point:
  - `prompt-answers!` – run the interactive flow and return an answers map."
  (:require [lanterna.terminal :as t]))


(defn- windows?
  []
  (-> (System/getProperty "os.name")
      .toLowerCase
      (.contains "win")))

(defn- make-terminal
  "Return a Lanterna terminal appropriate for the OS.
   - On Unix/macOS: text console.
   - On Windows: let Lanterna pick (usually Swing), avoiding Cygwin/stty.exe."
  []
  (if (windows?)
    (t/get-terminal :auto)   ;; or :swing if you want to *force* a Swing window
    (t/get-terminal :text)))


;; -----------------------------
;; Options (label + key)
;; -----------------------------

(def ^:private ci-options
  [{:label "GitHub"       :key :github}
   {:label "Azure DevOps" :key :azure}])

(def ^:private project-type-options
  [{:label "Python Library"          :key :python-lib}
   {:label "Databricks Asset Bundle" :key :dabs}])

;; -----------------------------
;; Low-level helpers
;; -----------------------------

(defn- read-line!
  "Read a line of input from the terminal, echoing as the user types.
   Returns the final string when Enter is pressed."
  [term]
  (loop [acc ""]
    (let [k (t/get-key-blocking term)]
      (cond
        (= k :enter)
        acc

        (char? k)
        (do
          (t/put-character term k)
          (recur (str acc k)))

        :else
        (recur acc)))))

(defn- ask-text!
  "Ask a single-line text question at `row`, return the answer.
   The final answer is coloured green in-place after Enter."
  [term {:keys [row question]}]
  (t/move-cursor term 0 row)
  (t/put-string term question)

  (t/move-cursor term (count question) row)
  (let [answer (read-line! term)]
    (t/move-cursor term (count question) row)
    (t/set-fg-color term :green)
    (t/put-string term answer)
    (t/set-fg-color term :default)
    answer))

(defn- clear-line-range!
  "Clear lines from `start-row` (inclusive) to `end-row` (inclusive)."
  [term start-row end-row]
  (let [blank (apply str (repeat 80 " "))]
    (doseq [row (range start-row (inc end-row))]
      (t/move-cursor term 0 row)
      (t/put-string term blank))))

(defn- select-option!
  "Generic arrow-key menu.
   `options` is a vector of maps {:label string, :key keyword}.
   Draws `question` at `row`, options below.
   Returns the selected option map and clears the menu rows."
  [term {:keys [row question options]}]
  (loop [idx 0]
    ;; Draw question
    (t/move-cursor term 0 row)
    (t/put-string term question)

    ;; Draw options
    (doseq [[i {:keys [label]}] (map-indexed vector options)]
      (t/move-cursor term 0 (+ row 1 i))
      (if (= i idx)
        (do
          (t/set-fg-color term :green)
          (t/put-string term (str "> " label))
          (t/set-fg-color term :default))
        (t/put-string term (str "  " label))))

    ;; Handle input
    (let [k        (t/get-key-blocking term)
          last-idx (dec (count options))]
      (cond
        (= k :up)
        (recur (if (zero? idx) last-idx (dec idx)))

        (= k :down)
        (recur (if (= idx last-idx) 0 (inc idx)))

        (= k :enter)
        (let [choice (nth options idx)]
          ;; Clear the menu rows (keep question line)
          (clear-line-range! term (inc row) (+ row (count options)))
          choice)

        :else
        (recur idx)))))

(defn- ask-menu!
  "Wrapper around `select-option!`.
   Shows the menu, then rewrites the question line as:
   \"<question> <label>\" with label in green.
   Returns the selected option map."
  [term {:keys [row question options] :as spec}]
  (let [choice (select-option! term spec)
        {:keys [label]} choice]
    ;; Rewrite the line as a simple summary
    (clear-line-range! term row row)
    (t/move-cursor term 0 row)
    (t/put-string term question)
    (t/put-string term " ")
    (t/set-fg-color term :green)
    (t/put-string term label)
    (t/set-fg-color term :default)
    choice))

;; -----------------------------
;; Summary
;; -----------------------------

(defn- show-summary!
  [term {:keys [project-name ci-choice project-type-choice databricks-host]}]
  (t/clear term)
  (t/move-cursor term 0 0)
  (t/put-string term "Summary")

  (t/put-string term "\n\nWhat is your project's name? ")
  (t/set-fg-color term :green)
  (t/put-string term project-name)
  (t/set-fg-color term :default)

  (t/put-string term "\nChoose your CI provider: ")
  (t/set-fg-color term :green)
  (t/put-string term (:label ci-choice))
  (t/set-fg-color term :default)

  (t/put-string term "\nChoose your project type: ")
  (t/set-fg-color term :green)
  (t/put-string term (:label project-type-choice))
  (t/set-fg-color term :default)

  (when databricks-host
    (t/put-string term "\nDatabricks host: ")
    (t/set-fg-color term :green)
    (t/put-string term databricks-host)
    (t/set-fg-color term :default))

  (t/put-string term "\n\nPress any key to exit.")
  (t/get-key-blocking term))

;; -----------------------------
;; In-terminal flow
;; -----------------------------

(defn- collect-answers-in-terminal!
  "Run the interactive flow inside an existing lanterna terminal.
   Returns a map:
   {:project-name string
    :ci-provider  keyword
    :project-type keyword
    :databricks-host (string | nil)}"
  [term]
  (t/clear term)

  (let [project-name (ask-text! term {:row 0
                                      :question "What is your project's name? "})

        ci-choice    (ask-menu! term {:row      1
                                      :question "Choose your CI provider:"
                                      :options  ci-options})

        project-type-choice (ask-menu! term {:row      2
                                             :question "Choose your project type:"
                                             :options  project-type-options})

        databricks-host
        (when (= (:key project-type-choice) :dabs)
          (ask-text! term {:row 3
                           :question "Enter your Databricks host name: "}))

        result {:project-name    project-name
                ;; match existing API: keywords like :github, :azure, :python-lib, :dabs
                :ci-provider     (:key ci-choice)
                :project-type    (:key project-type-choice)
                :databricks-host databricks-host}]
    (show-summary! term {:project-name        project-name
                         :ci-choice           ci-choice
                         :project-type-choice project-type-choice
                         :databricks-host     databricks-host})
    result))

;; -----------------------------
;; Public API
;; -----------------------------

(defn prompt-answers!
  []
  (let [term (make-terminal)
        {:keys [project-name ci-provider project-type databricks-host]}
        (t/in-terminal term
          (collect-answers-in-terminal! term))]
    (cond-> {:project-name project-name
             :ci-provider  ci-provider
             :project-type project-type}
      databricks-host (assoc :hostname databricks-host))))


