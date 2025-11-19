(ns blueprint.tui.menu-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [blueprint.tui.menu :as menu]))

(defn- seq-reader
  "Given a vector of key codes, return a function that yields one code
   per call, advancing through the vector."
  [codes]
  (let [state (atom codes)]
    (fn []
      (let [xs @state
            c  (first xs)]
        (swap! state subvec 1)
        c))))

(deftest valid-option?-recognises-correct-shape
  (testing "valid-option? returns true only for [keyword label] pairs"
    (is (true? (#'menu/valid-option? [:github "GitHub"])))
    (is (false? (#'menu/valid-option? [:github 123])))
    (is (false? (#'menu/valid-option? ["github" "GitHub"])))
    (is (false? (#'menu/valid-option? [:github "GitHub" "extra"])))
    (is (false? (#'menu/valid-option? :github)))))

(deftest validate-options!-accepts-well-formed-options
  (testing "validate-options! returns the options when they are valid"
    (let [opts [[:github "GitHub"] [:azure "Azure DevOps"]]]
      (is (= opts (#'menu/validate-options! opts))))))

(deftest validate-options!-rejects-bad-input
  (testing "validate-options! throws on non-sequential / empty / malformed options"
    (is (thrown? clojure.lang.ExceptionInfo
                 (#'menu/validate-options! nil)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (#'menu/validate-options! [])))
    (is (thrown? clojure.lang.ExceptionInfo
                 (#'menu/validate-options! [[:github "GitHub"] ["azure" "Azure"]])))))

(deftest run-menu-loop-enter-selects-first-option
  (testing "pressing Enter immediately selects the first option"
    (let [options [[:github "GitHub"]
                   [:azure  "Azure DevOps"]]
          read-ch (seq-reader [13])          ;; just Enter
          render! (fn [_] nil)
          result  (#'menu/run-menu-loop options read-ch render!)]
      (is (= :github result)))))

(deftest run-menu-loop-down-then-enter-selects-second-option
  (testing "Arrow Down followed by Enter selects the second option"
    (let [options [[:github "GitHub"]
                   [:azure  "Azure DevOps"]]
          ;; ESC [ B (Arrow Down), then Enter
          read-ch (seq-reader [27 91 66 13])
          render! (fn [_] nil)
          result  (#'menu/run-menu-loop options read-ch render!)]
      (is (= :azure result)))))

(deftest run-menu-loop-ignores-non-esc-non-enter-keys
  (testing "Random keys (e.g. letters) are ignored and do not change selection"
    (let [options [[:github "GitHub"]
                   [:azure  "Azure DevOps"]]
          ;; 'a' (97), 'b' (98), Enter
          read-ch (seq-reader [97 98 13])
          calls   (atom [])
          render! (fn [idx] (swap! calls conj idx))
          result  (#'menu/run-menu-loop options read-ch render!)]
      (is (= :github result)
          "still selects first option")
      (is (= [] @calls)
          "render! is never called"))))

(deftest run-menu-loop-ignores-unknown-esc-sequences
  (testing "Unknown ESC sequences are ignored and do not change selection"
    (let [options [[:github "GitHub"]
                   [:azure  "Azure DevOps"]]
          ;; ESC 'X' (27, 88) -> not '['; then Enter
          read-ch (seq-reader [27 88 13])
          calls   (atom [])
          render! (fn [idx] (swap! calls conj idx))
          result  (#'menu/run-menu-loop options read-ch render!)]
      (is (= :github result)
          "selection remains on first option")
      (is (= [] @calls)
          "render! is never called for unknown ESC sequence"))))

(deftest run-menu-loop-renders-on-each-real-move
  (testing "render! is called only when the selected index actually changes"
    (let [options [[:github "GitHub"]
                   [:azure  "Azure DevOps"]
                   [:third  "Third Option"]]
          ;; Down, down, up, Enter -> indices: 0 -> 1 -> 2 -> 1
          read-ch (seq-reader [27 91 66   ;; down to 1
                               27 91 66   ;; down to 2
                               27 91 65   ;; up to 1
                               13])
          calls   (atom [])
          render! (fn [idx] (swap! calls conj idx))
          result  (#'menu/run-menu-loop options read-ch render!)]
      (is (= :azure result)
          "final selection is the second option (index 1)")
      (is (= [1 2 1] @calls)
          "render! called for each index change in order"))))
