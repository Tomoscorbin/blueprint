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

(def ^:private zero-code
  "Integer code point for character '0'."
  48)

(def ^:private nine-code
  "Integer code point for character '9'."
  57)

(def ^:private semicolon-code
  "Integer code point for character ';'."
  59)

(def ^:private enter-key-codes
  "Integer key codes that should be treated as Enter (CR and LF)."
  #{13 10})

(def ^:private escape-key-code
  "Integer key code for the Escape key."
  27)

(def ^:private eof-code
  "Reader EOF sentinel; JLine returns -1 when the input stream is closed."
  -1)

(def ^:private csi-int
  "Second byte for CSI-based arrow-key escape sequences: ESC [ ..."
  91) ; '['

(def ^:private ss3-int
  "Second byte for SS3-based (application mode) arrow-key sequences: ESC O ..."
  79) ; 'O'

(def ^:private final-up-arrow-code
  "Final byte for the Up arrow ANSI sequence (A/B/C/D)."
  65) ; 'A'

(def ^:private final-down-arrow-code
  "Final byte for the Down arrow ANSI sequence (A/B/C/D)."
  66) ; 'B'

(def ^:private final-right-arrow-code
  "Final byte for the Right arrow ANSI sequence (A/B/C/D)."
  67) ; 'C'

(def ^:private final-left-arrow-code
  "Final byte for the Left arrow ANSI sequence (A/B/C/D)."
  68) ; 'D'

(def ^:private arrow-final-codes
  "Set of all recognised final arrow-key codes (we only map up/down)."
  #{final-up-arrow-code
    final-down-arrow-code
    final-right-arrow-code
    final-left-arrow-code})

(def ^:private final-arrow-code->direction
  "Mapping from final arrow-key byte code to the up/down directions."
  {final-up-arrow-code   :up
   final-down-arrow-code :down})

(def ^:private pointer
  "Menu pointer symbol; uses ASCII on Windows to avoid Unicode rendering issues."
  (if (term/windows?) ">" "▶"))

(defn- goto-line!
  "Move the cursor to line `n` relative to the saved cursor position.
   Lines are 1-based, so n=1 means 'saved cursor line'."
  [n]
  (term/restore-cursor!)
  (when (> n 1)
    ;; 'E' moves down by the given number of lines, to column 1.
    (term/print-ansi! term/esc (dec n) "E")))

(defn- print-option!
  "Print a single menu option line at the current cursor position,
   clearing the line first and applying highlighting if selected."
  [label selected?]
  (term/clear-line!)
  (if selected?
    (println (str pointer " " (term/green-bold label)))
    (println label))
  (flush))

(defn- delete-options-block!
  "Delete `line-count` lines of menu output starting from the first option line
   (relative to the saved cursor)."
  [line-count]
  ;; Go to first option line relative to the saved cursor, then delete n lines.
  (goto-line! 2)
  (term/print-ansi! term/esc line-count "M")) ; CSI n M (DL = Delete Line)

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
  "Return true if c is the code point for '0'-'9' or ';'."
  [c]
  (or (and (>= c zero-code) (<= c nine-code))
      (= c semicolon-code)))

(defn- read-arrow-key
  "Assuming the initial ESC has already been read, consume the rest of an
  arrow-key sequence from `read-ch` and return a high-level direction:

  - :up, :down for recognised arrows
  - nil if the sequence is not recognised or incomplete"
  [read-ch]
  (let [c2 (read-ch)]
    (if (or (= c2 csi-int)   ; ESC [ ...
            (= c2 ss3-int))  ; ESC O ...
      (let [final (loop [c (read-ch)]
                    (cond
                      ;; end-of-stream or error → give up
                      (or (nil? c) (= c eof-code))
                      nil

                      ;; final arrow keys: A/B/C/D
                      (arrow-final-codes c)
                      c

                      ;; if we see digits or ';', keep reading (e.g. ESC [ 1 ; 5 A)
                      (digit-or-semicolon? c)
                      (recur (read-ch))

                      ;; something else we don't understand → give up
                      :else
                      nil))]
        ;; Only up/down are mapped; left/right still result in nil.
        (when final
          (get final-arrow-code->direction final)))
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
      (contains? enter-key-codes ch)
      :enter

      ;; ESC … (arrow keys etc.)
      (= ch escape-key-code)
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
  (let [options-vec (vec options)
        ;; blank line + options
        line-count  (inc (count options-vec))
        ^Terminal t (term/terminal)
        rdr         (.reader t)
        prev-attr   (.enterRawMode t)]
    (try
      ;; initial render
      (term/save-cursor!)
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
