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

(defn- save-cursor!    [] (print (str esc "s")) (flush))
(defn- restore-cursor! [] (print (str esc "u")) (flush))
(defn- clear-line!     [] (print (str esc "2K")) (flush))

(defn- goto-line!
  "Move the cursor to line `n` relative to the saved cursor position.
   Lines are 1-based, so n=1 means 'saved cursor line'."
  [n]
  (restore-cursor!)
  (when (> n 1)
    ;; 'E' moves down by the given number of lines, to column 1.
    (print (str esc (dec n) "E")))
  (flush))

(defn- green-bold [s]
  (str esc "1;32m" s esc "0m"))

(def ^:private pointer
  ;; Use a simple ASCII pointer on Windows to avoid Unicode rendering issues
  (if (term/windows?) ">" "▶"))

(defn- print-option!
  "Print a single menu option line at the current cursor position,
   clearing the line first and applying highlighting if selected."
  [label selected?]
  (clear-line!)
  (if selected?
    (println (str pointer " " (green-bold label)))
    (println label))) ; no indent on unselected

(defn- delete-options-block!
  "Delete `n` lines of menu output starting from the first option line
   (relative to the saved cursor)."
  [n]
  ;; go to first option line relative to the saved cursor, then delete n lines
  (goto-line! 2)
  (print (str esc n "M")) ; CSI n M  (DL = Delete Line)
  (flush))

;; --- Validation ------------------------------------------------------------

(defn- valid-option?
  "Return true if x is a [keyword label] pair."
  [x]
  (and (vector? x)
       (= 2 (count x))
       (keyword? (first x))
       (string? (second x))))

(defn- validate-options!
  "Ensure options is a non-empty sequence of [keyword label] pairs."
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
  ;; first option line is 2 lines below saved cursor (blank line between prompt and menu)
  (goto-line! 2)
  (doseq [[i [_ label]] (map-indexed vector options-vec)]
    (print-option! label (= i selected-index)))
  (flush))

;; --- Escape-sequence handling ----------------------------------------------

(defn- digit-or-semicolon?
  "Return true if c is the code point for '0'–'9' or ';'."
  [c]
  (or (and (>= c 48) (<= c 57)) ; '0'..'9'
      (= c 59)))               ; ';'

(defn- handle-escape
  "Handle an ESC sequence starting from the character after ESC.

  Reads any additional bytes needed to determine whether this is an up/down
  arrow, including longer CSI forms like ESC [ 1 ; 5 A.

  Returns the new selected index (which may be unchanged)."
  [idx last-index read-ch render!]
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
          ;; up
          65 (let [new (max 0 (dec idx))]
               (when (not= new idx)
                 (render! new))
               new)

          ;; down
          66 (let [new (min last-index (inc idx))]
               (when (not= new idx)
                 (render! new))
               new)

          ;; anything else -> no change
          idx))
      ;; not a CSI/SS3 sequence we care about → ignore
      idx)))

;; --- Arrow-key menu core ---------------------------------------------------

(defn- run-menu-loop
  "Core menu loop: reads key codes via `read-ch`, tracks selected index,
  calls `render!` whenever the selection changes, and returns the selected key
  when Enter is pressed.

  `options-vec` must be a vector of [key label] pairs."
  [options-vec read-ch render!]
  (let [last-index (dec (count options-vec))]
    (loop [idx 0]
      (let [ch (read-ch)]
        (cond
          ;; Enter: return key of current option
          (or (= ch 13) (= ch 10))          ; CR or LF
          (first (nth options-vec idx))

          ;; ESC … (arrow keys etc.)
          (= ch 27)                         ; ESC
          (recur (handle-escape idx last-index read-ch render!))

          ;; all other keys ignore and keep current selection
          :else
          (recur idx))))))

(defn- create-arrow-menu!
  "Arrow-key menu implementation for arrow-menu-supported terminals."
  [options]
  (validate-options! options)
  (let [options-vec (vec options)
        lines       (inc (count options-vec)) ; blank line + options
        ^Terminal t (term/terminal)
        rdr         (.reader t)
        prev-attr   (.enterRawMode t)]
    (try
      ;; initial render
      (save-cursor!)
      (render-options-block! options-vec 0)
      (goto-line! (inc lines))
      (println)
      (flush)

      ;; delegate to core loop
      (let [read-ch (fn [] (.read rdr))
            render! (fn [selected-index]
                      (render-options-block! options-vec selected-index))]
        (run-menu-loop options-vec read-ch render!))

      (finally
        (delete-options-block! lines)
        (.setAttributes t prev-attr)))))

;; --- Fallback numeric menu -------------------------------------------------

(defn- create-fallback-menu!
  "Numeric menu for terminals where raw keys aren't reliable (e.g. plain
   Windows consoles, 'dumb' CI environments)."
  [options]
  (validate-options! options)
  (let [options-vec (vec options)]
    (loop []
      (println)
      (doseq [[i [_ label]] (map-indexed vector options-vec)]
        (println (format "  [%d] %s" (inc i) label)))
      (print (format "Enter choice [1-%d]: " (count options-vec)))
      (flush)
      (let [input (read-line)
            idx   (try
                    (dec (Integer/parseInt (str/trim input)))
                    (catch Exception _
                      ::invalid))]
        (if (and (integer? idx)
                 (<= 0 idx (dec (count options-vec))))
          (first (nth options-vec idx))
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
