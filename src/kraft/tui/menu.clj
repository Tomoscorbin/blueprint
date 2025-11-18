(ns kraft.tui.menu
  "Inline arrow-key menu rendered directly to the terminal using ANSI escape codes
   and JLine for raw input."
  (:import [org.jline.terminal TerminalBuilder Terminal]))

(def ^:private ^Terminal terminal
  (delay
    (.build (TerminalBuilder/builder))))

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

(defn- print-option!
  "Print a single menu option line at the current cursor position,
   clearing the line first and applying highlighting if selected."
  [label selected?]
  (clear-line!)
  (if selected?
    (println (str "▶ " (green-bold label)))
    (println label))) ; no indent on unselected

(defn- delete-options-block!
  "Delete `n` lines of menu output starting from the first option line
   (relative to the saved cursor)."
  [n]
  ;; go to first option line relative to saved cursor, then delete n lines
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

;; --- Rendering -------------------------------------------------------------

(defn- render-options-block!
  "Render the full options block starting from the saved cursor.

  `opts` is a vector of [key label] pairs.
  `selected-idx` is the index of the currently selected option."
  [opts selected-idx]
  ;; first option line is 2 lines below saved cursor (blank line between prompt and menu)
  (goto-line! 2)
  (doseq [[i [_ label]] (map-indexed vector opts)]
    (print-option! label (= i selected-idx)))
  (flush))

;; --- Public menu -----------------------------------------------------------

(defn- run-menu-loop
  "Core menu loop: reads key codes via `read-ch`, tracks selected index,
  calls `render!` whenever the selection changes, and returns the selected key
  when Enter is pressed.

  `opts` must be a vector of [key label] pairs."
  [opts read-ch render!]
  (loop [idx 0]
    (let [ch (read-ch)]
      (cond
        ;; Enter: return key of current option
        (= ch 13)
        (first (nth opts idx))

        ;; ESC [ A/B (arrow keys)
        (= ch 27)
        (let [c2 (read-ch)]
          (if (= c2 91)
            (let [c3 (read-ch)]
              (case c3
                ;; up
                65 (let [new (max 0 (dec idx))]
                     (when (not= new idx)
                       (render! new))
                     (recur new))
                ;; down
                66 (let [new (min (dec (count opts)) (inc idx))]
                     (when (not= new idx)
                       (render! new))
                     (recur new))
                ;; unknown escape sequence: ignore
                (recur idx)))
            ;; not an ESC-[ sequence: ignore
            (recur idx)))

        ;; all other keys ignore and keep current selection
        :else
        (recur idx)))))

(defn create-menu!
  [options]
  (validate-options! options)
  (let [opts      (vec options)
        lines     (inc (count opts))       ; blank line + options
        ^Terminal term @terminal
        rdr       (.reader term)
        prev-attr (.enterRawMode term)]
    (try
      ;; initial render
      (save-cursor!)
      (render-options-block! opts 0)
      (goto-line! (inc lines))
      (println)
      (flush)

      ;; delegate to pure-ish loop
      (let [read-ch (fn [] (.read rdr))
            render! (fn [selected-idx]
                      (render-options-block! opts selected-idx))]
        (run-menu-loop opts read-ch render!))

      (finally
        (delete-options-block! lines)
        (.setAttributes term prev-attr)
        (.close rdr)))))

