(ns blueprint.tui.menu-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [blueprint.tui.menu :as menu]
   [blueprint.tui.terminal :as term]))

;; --- Helpers -----------------------------------------------------------------

(defn- seq-reader
  "Given a vector of key codes, return a function that yields one code
   per call, advancing through the vector.

   Once exhausted, further calls will return nil (simulating 'no more input')."
  [codes]
  (let [state (atom (vec codes))]
    (fn []
      (let [xs @state
            c  (first xs)]
        (swap! state #(if (seq %) (subvec % 1) %))
        c))))

(defn- make-options
  "Standard options used in most tests."
  []
  [[:github "GitHub"]
   [:azure  "Azure DevOps"]
   [:third  "Third Option"]])

;; --- run-menu-loop behaviour -------------------------------------------------

(deftest run-menu-loop-enter-selects-first-option
  (testing "Enter immediately selects the first option"
    ;; given
    (let [options (make-options)
          read-ch (seq-reader [13])       ;; CR
          render! (fn [_] nil)]
      ;; when
      (let [result (#'menu/run-menu-loop options read-ch render!)]
        ;; then
        (is (= :github result))))))

(deftest run-menu-loop-accepts-lf-as-enter
  (testing "LF (10) is also treated as Enter"
    (let [options (make-options)
          read-ch (seq-reader [10])       ;; LF
          render! (fn [_] nil)]
      (let [result (#'menu/run-menu-loop options read-ch render!)]
        (is (= :github result))))))

(deftest run-menu-loop-down-then-enter-selects-second-option
  (testing "Arrow Down followed by Enter selects the second option"
    ;; ESC [ B (Down), then Enter
    (let [options (make-options)
          read-ch (seq-reader [27 91 66 13])
          render! (fn [_] nil)]
      (let [result (#'menu/run-menu-loop options read-ch render!)]
        (is (= :azure result))))))

(deftest run-menu-loop-ignores-random-keys
  (testing "Non-ESC, non-Enter keys are ignored and do not change selection"
    ;; 'a' (97), 'b' (98), Enter
    (let [options (make-options)
          read-ch (seq-reader [97 98 13])
          calls   (atom [])
          render! (fn [idx] (swap! calls conj idx))]
      (let [result (#'menu/run-menu-loop options read-ch render!)]
        (is (= :github result) "still selects first option")
        (is (= [] @calls) "render! is never called")))))

(deftest run-menu-loop-ignores-unknown-esc-sequences
  (testing "Unknown ESC sequences are ignored and do not change selection"
    ;; ESC 'X' (27, 88) -> not '[' or 'O'; then Enter
    (let [options (make-options)
          read-ch (seq-reader [27 88 13])
          calls   (atom [])
          render! (fn [idx] (swap! calls conj idx))]
      (let [result (#'menu/run-menu-loop options read-ch render!)]
        (is (= :github result)
            "selection remains on first option")
        (is (= [] @calls)
            "render! is never called for unknown ESC sequence")))))

(deftest run-menu-loop-ignores-left-and-right-arrows
  (testing "Left and Right arrows are ignored (no selection change)"
    ;; ESC [ C (Right), ESC [ D (Left), then Enter
    (let [options (make-options)
          read-ch (seq-reader [27 91 67   ;; Right
                               27 91 68   ;; Left
                               13])
          calls   (atom [])
          render! (fn [idx] (swap! calls conj idx))]
      (let [result (#'menu/run-menu-loop options read-ch render!)]
        (is (= :github result))
        (is (= [] @calls)
            "render! is never called for horizontal arrows")))))

(deftest run-menu-loop-renders-on-each-real-move
  (testing "render! is called only when the selected index actually changes"
    ;; Down, down, up, Enter -> indices: 0 -> 1 -> 2 -> 1
    (let [options (make-options)
          read-ch (seq-reader [27 91 66   ;; down to 1
                               27 91 66   ;; down to 2
                               27 91 65   ;; up to 1
                               13])
          calls   (atom [])
          render! (fn [idx] (swap! calls conj idx))]
      (let [result (#'menu/run-menu-loop options read-ch render!)]
        (is (= :azure result)
            "final selection is the second option (index 1)")
        (is (= [1 2 1] @calls)
            "render! called for each index change in order")))))

(deftest run-menu-loop-does-not-move-above-first-option
  (testing "Up arrow from the first option does not move or re-render"
    ;; ESC [ A (Up), then Enter
    (let [options (make-options)
          read-ch (seq-reader [27 91 65 13])
          calls   (atom [])
          render! (fn [idx] (swap! calls conj idx))]
      (let [result (#'menu/run-menu-loop options read-ch render!)]
        (is (= :github result))
        (is (= [] @calls)
            "no re-render when already at top")))))

(deftest run-menu-loop-does-not-move-below-last-option
  (testing "Down arrow from the last option does not move or re-render"
    ;; 2 options only; go down into last, then keep pressing Down
    (let [options [[:github "GitHub"]
                   [:azure  "Azure DevOps"]]
          read-ch (seq-reader [27 91 66   ;; 0 -> 1
                               27 91 66   ;; stay at 1
                               27 91 66   ;; stay at 1
                               13])
          calls   (atom [])
          render! (fn [idx] (swap! calls conj idx))]
      (let [result (#'menu/run-menu-loop options read-ch render!)]
        (is (= :azure result))
        (is (= [1] @calls)
            "render! called only for the real move from 0 -> 1")))))

(deftest run-menu-loop-supports-extended-arrow-sequences
  (testing "Arrow keys with CSI parameters (ESC [ 1 ; 5 B) still work"
    ;; ESC [ 1 ; 5 B (Down with modifiers), then Enter
    (let [options (make-options)
          read-ch (seq-reader [27 91 49 59 53 66 13])
          calls   (atom [])
          render! (fn [idx] (swap! calls conj idx))]
      (let [result (#'menu/run-menu-loop options read-ch render!)]
        (is (= :azure result)
            "extended Down sequence still moves to second option")
        (is (= [1] @calls)
            "render! called once for the move from 0 -> 1")))))

;; --- parse-choice behaviour -------------------------------------------------

(deftest parse-choice-valid-input
  (testing "Valid numeric strings are parsed as zero-based indices"
    (let [idx1 (#'menu/parse-choice "1")
          idx2 (#'menu/parse-choice "  2  ")]
      (is (= 0 idx1))
      (is (= 1 idx2)))))

(deftest parse-choice-invalid-input
  (testing "Non-numeric inputs are treated as ::menu/invalid"
    (let [idx1 (#'menu/parse-choice "")
          idx2 (#'menu/parse-choice "abc")
          idx3 (#'menu/parse-choice "1.5")]
      (is (= ::menu/invalid idx1))
      (is (= ::menu/invalid idx2))
      (is (= ::menu/invalid idx3)))))

(deftest parse-choice-allows-negative-and-zero-values
  (testing "Negative and zero still parse numerically (range is validated later)"
    (let [idx0  (#'menu/parse-choice "0")
          idx-1 (#'menu/parse-choice "-1")]
      (is (= -1 idx0) "0 -> index -1")
      (is (= -2 idx-1) "-1 -> index -2"))))

;; --- Fallback numeric menu behaviour ----------------------------------------

(deftest fallback-menu-selects-correct-option
  (testing "A single valid numeric choice selects the expected option"
    (let [options [[:github "GitHub"]
                   [:azure  "Azure DevOps"]]
          output  (java.io.StringWriter.)]
      (let [result (binding [*out* output]
                     (with-in-str "2\n"
                       (#'menu/create-fallback-menu! options)))]
        (is (= :azure result))))))

(deftest fallback-menu-reprompts-on-invalid-input
  (testing "Invalid numeric input reprompts until a valid selection is entered"
    (let [options [[:github "GitHub"]
                   [:azure  "Azure DevOps"]]
          output  (java.io.StringWriter.)]
      (let [result (binding [*out* output]
                     (with-in-str "99\nfoo\n1\n"
                       (#'menu/create-fallback-menu! options)))]
        (is (= :github result)
            "eventually returns the first option after bad inputs")))))

;; --- create-menu! dispatch ---------------------------------------------------

(deftest create-menu-uses-arrow-menu-when-supported
  (testing "create-menu! delegates to the arrow-key menu on supported terminals"
    (let [options     [[:github "GitHub"]
                       [:azure  "Azure DevOps"]]
          called-with (atom nil)]
      (with-redefs [term/arrow-menu-supported? (constantly true)
                    menu/create-arrow-menu!    (fn [opts]
                                                 (reset! called-with opts)
                                                 :arrow-result)]
        (let [result (menu/create-menu! options)]
          (is (= :arrow-result result))
          (is (= options @called-with)))))))

(deftest create-menu-uses-fallback-when-arrow-menu-not-supported
  (testing "create-menu! delegates to the fallback menu when arrow menu is not supported"
    (let [options     [[:github "GitHub"]
                       [:azure  "Azure DevOps"]]
          called-with (atom nil)]
      (with-redefs [term/arrow-menu-supported? (constantly false)
                    menu/create-fallback-menu! (fn [opts]
                                                 (reset! called-with opts)
                                                 :fallback-result)]
        (let [result (menu/create-menu! options)]
          (is (= :fallback-result result))
          (is (= options @called-with)))))))
