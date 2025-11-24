(ns blueprint.tui.menu
  "Inline menu rendered directly to the terminal using ANSI escape codes
   and JLine for raw input where supported.

   Behaviour:
   - On arrow-menu-supported terminals (Linux/macOS terminals, Git Bash),
     we use an arrow-key menu.
   - On plain Windows consoles, we fall back to a numeric menu."
  (:require
   [clojure.string :as str]
   [blueprint.tui.terminal :as term])
  (:import
   [org.jline.terminal Terminal]))

;; --- ANSI helpers ----------------------------------------------------------

(def ^:private esc "\u001b[")

(defn- print-ansi!
  "Print a raw ANSI sequence (or plain text) and flush immediately."
  [& parts]
  (print (apply str parts))
  (flush))

(defn- save-cursor!    [] (print-ansi! esc "s"))
(defn- restore-cursor! [] (print-ansi! esc "u"))
(defn- clear-line!     [] (print-ansi! esc "2K"))

(defn- goto-line!
  "Move the cursor to line `n` relative to the saved cursor position.
   Lines are 1-based, so n=1 means 'saved cursor line'."
  [n]
  (restore-cursor!)
  (when (> n 1)
    ;; 'E' moves down by the given number of lines, to column 1.
    (print-ansi! esc (dec n) "E")))

(defn- green-bold
  "Wrap `s` in a bold green SGR sequence."
  [s]
  (str esc "1;32m" s esc "0m"))

(def ^:private pointer
  ;; Use an ASCII pointer on Windows to avoid Unicode rendering issues.
  (if (term/windows?) ">" "▶"))

(defn- print-option!
  "Print a single menu option line at the current cursor position,
   clearing the line first and applying highlighting if selected."
  [label selected?]
  (clear-line!)
  (if selected?
    (println (str pointer " " (green-bold label)))
    (println label))
  (flush))

(defn- delete-options-block!
  "Delete `line-count` lines of menu output starting from the first option line
   (relative to the saved cursor)."
  [line-count]
  ;; Go to first option line relative to the saved cursor, then delete n lines.
  (goto-line! 2)
  (print-ansi! esc line-count "M")) ; CSI n M (DL = Delete Line)

;; --- Validation ------------------------------------------------------------

(defn- valid-option?
  "Return true if x is a [keyword label] pair."
  [x]
  (and (vector? x)
       (= 2 (count x))
       (keyword? (first x))
       (string? (second x))))

(defn- validate-options!
  "Ensure options is a non-empty sequence of [keyword label] pairs.
   Returns the options unchanged if valid, otherwise throws."
  [options]
  (when (or (not (sequential? options))
            (empty? options)
            (not (every? valid-option? options)))
    (throw (ex-info "options must be a non-empty sequence of [keyword label] pairs"
                    {:options options})))
  options)

;; --- Rendering block for arrow menu ----------------------------------------

(defn- render-options-block!
  "Render the full options block starting from the saved cursor.

  `options-vec` is a vector of [key label] pairs.
  `selected-index` is the index of the currently selected option."
  [options-vec selected-index]
  ;; First option line is 2 lines below saved cursor
  ;; (blank line between prompt and menu).
  (goto-line! 2)
  (doseq [[i [_ label]] (map-indexed vector options-vec)]
    (print-option! label (= i selected-index))))

;; --- Escape-sequence / key handling ----------------------------------------

(defn- digit-or-semicolon?
  "Return true if c is the code point for '0-'9' or ';'."
  [c]
  (or (and (>= c 48) (<= c 57)) ; '0'..'9'
      (= c 59)))                ; ';'

(defn- read-arrow-key
  "Assuming the initial ESC has already been read, consume the rest of an
  arrow-key sequence from `read-ch` and return a high-level direction:

  - :up, :down for recognised arrows
  - nil if the sequence is not recognised or incomplete"
  [read-ch]
  (let [c2 (read-ch)]
    (if (or (= c2 91)  ; '['  (CSI)
            (= c2 79)) ; 'O'  (SS3 / application mode)
      (let [final (loop [c (read-ch)]
                    (cond
                      ;; end-of-stream or error → give up
                      (or (nil? c) (= c -1))
                      nil

                      ;; final arrow keys: A/B/C/D
                      (or (= c 65) (= c 66) (= c 67) (= c 68))
                      c

                      ;; if we see digits or ';', keep reading (e.g. ESC [ 1 ; 5 A)
                      (digit-or-semicolon? c)
                      (recur (read-ch))

                      ;; something else we do not understand → give up
                      :else
                      nil))]
        (case final
          65 :up    ; 'A'
          66 :down  ; 'B'
          ;; Left/right are ignored for now.
          nil))
      ;; Not a CSI/SS3 sequence we care about.
      nil)))

(defn- read-key-event
  "Read a single high-level key event from the terminal via `read-ch`.

  Returns one of:
  - :enter
  - :up
  - :down
  - :other (anything else)"
  [read-ch]
  (let [ch (read-ch)]
    (cond
      ;; Enter: CR or LF
      (or (= ch 13) (= ch 10))
      :enter

      ;; ESC … (arrow keys etc.)
      (= ch 27)
      (or (read-arrow-key read-ch) :other)

      :else
      :other)))

;; --- Arrow-key menu core ---------------------------------------------------

(defn- option-key-at
  "Return the option keyword at `index` in `options-vec`."
  [options-vec index]
  (-> options-vec (nth index) first))

(defn- run-menu-loop
  "Core menu loop: reads key codes via `read-ch`, tracks selected index,
  calls `render!` whenever the selection changes, and returns the selected key
  when Enter is pressed.

  `options-vec` must be a vector of [key label] pairs."
  [options-vec read-ch render!]
  (let [last-index (dec (count options-vec))]
    (loop [selected-index 0]
      (case (read-key-event read-ch)
        :enter
        (option-key-at options-vec selected-index)

        :up
        (let [new-index (max 0 (dec selected-index))]
          (when (not= new-index selected-index)
            (render! new-index))
          (recur new-index))

        :down
        (let [new-index (min last-index (inc selected-index))]
          (when (not= new-index selected-index)
            (render! new-index))
          (recur new-index))

        ;; :other
        (recur selected-index)))))

(defn- create-arrow-menu!
  "Arrow-key menu implementation for arrow-menu-supported terminals."
  [options]
  (validate-options! options)
  (let [options-vec (vec options)
        ;; blank line + options
        line-count  (inc (count options-vec))
        ^Terminal t (term/terminal)
        rdr         (.reader t)
        prev-attr   (.enterRawMode t)]
    (try
      ;; initial render
      (save-cursor!)
      (render-options-block! options-vec 0)
      (goto-line! (inc line-count))
      (println)
      (flush)

      ;; delegate to core loop
      (let [read-ch (fn [] (.read rdr))
            render! (fn [selected-index]
                      (render-options-block! options-vec selected-index))]
        (run-menu-loop options-vec read-ch render!))

      (finally
        (delete-options-block! line-count)
        (.setAttributes t prev-attr)))))

;; --- Fallback numeric menu -------------------------------------------------

(defn- parse-choice
  "Parse a 1-based numeric choice from `s`, returning zero-based index or
   ::invalid on failure."
  [s]
  (try
    (let [trimmed (str/trim s)
          n       (Integer/parseInt trimmed)
          idx     (dec n)]
      idx)
    (catch Exception _
      ::invalid)))

(defn- create-fallback-menu!
  "Numeric menu for terminals where raw keys aren't reliable (e.g. plain
   Windows consoles)."
  [options]
  (validate-options! options)
  (let [options-vec (vec options)
        max-index   (dec (count options-vec))]
    (loop []
      (println)
      (doseq [[i [_ label]] (map-indexed vector options-vec)]
        (println (format "  [%d] %s" (inc i) label)))
      (print (format "Enter choice [1-%d]: " (count options-vec)))
      (flush)
      (let [input (read-line)
            idx   (parse-choice input)]
        (if (and (integer? idx)
                 (<= 0 idx max-index))
          (option-key-at options-vec idx)
          (do
            (println "Invalid choice, try again.")
            (recur)))))))

;; --- Public API ------------------------------------------------------------

(defn create-menu!
  "Render an inline menu.

  On arrow-menu-supported terminals (Linux/macOS terminals, Git Bash),
  this is an arrow-key-driven menu.

  On plain Windows consoles, this falls back to a numeric menu."
  [options]
  (if (term/arrow-menu-supported?)
    (create-arrow-menu! options)
    (create-fallback-menu! options)))
