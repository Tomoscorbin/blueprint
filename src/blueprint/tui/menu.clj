(ns blueprint.tui.menu
  "Inline menu rendered directly to the terminal using ANSI escape codes
   and JLine for raw input where supported.

   Behaviour:
   - On 'arrow-menu-supported' terminals (Linux/macOS terminals, Windows Terminal / PowerShell),
     we use an arrow-key menu.
   - On MSYS / Git Bash and 'dumb' terminals, we fall back to a numeric menu."
  (:require
   [clojure.string :as str])
  (:import
   [org.jline.terminal TerminalBuilder Terminal]))

;; --- Environment / terminal detection --------------------------------------

(def ^:private windows?
  (-> (System/getProperty "os.name")
      (.toLowerCase)
      (.contains "win")))

(defn- msys? []
  ;; Present in Git Bash / MSYS environments on Windows
  (some? (System/getenv "MSYSTEM")))

(def ^:private ^Terminal terminal
  (delay
    (let [builder (TerminalBuilder/builder)]
      (-> builder
          ;; On plain Windows consoles, use the system terminal integration.
          ;; On MSYS / Git Bash, avoid the Windows system terminal and treat it
          ;; as an external terminal.
          (.system (not (msys?)))
          (.streams System/in System/out)
          (.build)))))

(defn- arrow-menu-supported?
  "Return true if this terminal is one where we are happy to use the arrow-key TUI.

  We explicitly disable the arrow menu on:
  - MSYS / Git Bash (MSYSTEM set), where key handling is flaky
  - 'dumb' terminals reported by JLine."
  [^Terminal term]
  (let [t (.getType term)]
    (and (not (msys?))
         (not= "dumb" t))))

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
  (if windows? ">" "▶"))

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

;; --- Arrow-key menu core ---------------------------------------------------

(defn- run-menu-loop
  "Core menu loop: reads key codes via `read-ch`, tracks selected index,
  calls `render!` whenever the selection changes, and returns the selected key
  when Enter is pressed.

  `options-vec` must be a vector of [key label] pairs."
  [options-vec read-ch render!]
  (loop [idx 0]
    (let [ch (read-ch)]
      (cond
        ;; Enter: return key of current option
        (= ch 13)                           ; Enter
        (first (nth options-vec idx))

        ;; ESC [ A/B (arrow keys)
        (= ch 27)                           ; ESC
        (let [c2 (read-ch)]
          (if (= c2 91)                     ; '['
            (let [c3 (read-ch)]
              (case c3
                ;; up
                65 (let [new (max 0 (dec idx))]
                     (when (not= new idx)
                       (render! new))
                     (recur new))
                ;; down
                66 (let [new (min (dec (count options-vec)) (inc idx))]
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

(defn- create-arrow-menu!
  "Arrow-key menu implementation for arrow-menu-supported terminals."
  [options]
  (validate-options! options)
  (let [options-vec (vec options)
        lines       (inc (count options-vec)) ; blank line + options
        ^Terminal term @terminal
        rdr         (.reader term)
        prev-attr   (.enterRawMode term)]
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
        (.setAttributes term prev-attr)))))
;; NOTE:
;; - JLine owns the Reader returned by (.reader term).
;; - Closing it here can race with internal reader threads (e.g. WindowsStreamPump)
;;   and cause IOExceptions on shutdown.
;; - We therefore never close `rdr` ourselves; we just restore the previous attributes.

;; --- Fallback numeric menu -------------------------------------------------

(defn- create-fallback-menu!
  "Numeric menu for terminals where raw keys aren't reliable (e.g. Git Bash/MSYS,
   'dumb' terminals, CI)."
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

  On arrow-menu-supported terminals (Linux/macOS terminals, Windows Terminal / PowerShell),
  this is an arrow-key-driven menu.

  On MSYS / Git Bash and 'dumb' terminals, this falls back to a numeric menu."
  [options]
  (let [^Terminal term @terminal]
    (if (arrow-menu-supported? term)
      (create-arrow-menu! options)
      (create-fallback-menu! options))))

