
(ns kraft.tui.menu
  (:import [org.jline.terminal TerminalBuilder]))

;; --- ANSI helpers ----------------------------------------------------------
(def ^:private esc "\u001b[")

(defn- save-cursor!    [] (print (str esc "s")) (flush))
(defn- restore-cursor! [] (print (str esc "u")) (flush))
(defn- clear-line!     [] (print (str esc "2K")) (flush))
(defn- goto-line!      [n] ; 1-based, from saved cursor
  (restore-cursor!)
  (when (> n 1) (print (str esc (dec n) "E")))
  (flush))
(defn- hide-cursor! [] (print (str esc "?25l")) (flush))
(defn- show-cursor! [] (print (str esc "?25h")))
(defn- green-bold [s] (str esc "1;32m" s esc "0m"))

(defn- print-option! [opt selected?]
  (clear-line!)
  (if selected?
    (println (str "▶ " (green-bold opt)))
    (println opt))) ; no indent on unselected

(defn- delete-options-block! [n]
  ;; go to first option line relative to saved cursor, then delete n lines
  (goto-line! 2)
  (print (str esc n "M")) ; CSI n M  (DL = Delete Line)
  (flush))

(defn create-menu!
  "Inline menu. Arrow up/down to move, Enter to select.
   `options` must be a vector of [key label]. Returns the selected key-value pair as a map."
  [options]
  (when (or (not (sequential? options)) (empty? options)
            (not (every? (fn [x] (and (vector? x)
                                      (= 2 (count x))
                                      (keyword? (first x))
                                      (string? (second x))))
                         options)))
    (throw (ex-info "options must be a vector of [keyword label] pairs" {:options options})))

  (let [opts  (vec options)
        lines (inc (count opts))
        term  (.build (TerminalBuilder/builder))
        rdr   (.reader term)]
    (try
      (.enterRawMode term)

      ;; initial render
      (hide-cursor!)
      (save-cursor!)
      (goto-line! 1)
      (doseq [[i [_ label]] (map-indexed vector opts)]
        (goto-line! (+ 2 i)) (print-option! label (= i 0)))
      ;; park cursor after block (so typing later doesn't overwrite it)
      (goto-line! (inc lines)) (println) (flush)

      (loop [idx 0]
        (let [ch (.read rdr)]
          (cond
            (= ch 13)                             ; Enter
            (first (nth opts idx))

            (= ch 27)                             ; ESC [ A/B
            (let [c2 (.read rdr)]
              (if (= c2 91)
                (let [c3 (.read rdr)]
                  (case c3
                    65 (let [new (max 0 (dec idx))] ; up
                         (when (not= new idx)
                           (goto-line! (+ 2 idx)) (print-option! (second (nth opts idx)) false)
                           (goto-line! (+ 2 new)) (print-option! (second (nth opts new)) true))
                         (recur new))
                    66 (let [new (min (dec (count opts)) (inc idx))] ; down
                         (when (not= new idx)
                           (goto-line! (+ 2 idx)) (print-option! (second (nth opts idx)) false)
                           (goto-line! (+ 2 new)) (print-option! (second (nth opts new)) true))
                         (recur new))
                    (recur idx)))
                (recur idx)))

            :else (recur idx))))
      (finally
        (delete-options-block! lines)
        (show-cursor!)
        (.close rdr)
        (.close term)))))
