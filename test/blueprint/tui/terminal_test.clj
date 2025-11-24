(ns blueprint.tui.terminal-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [blueprint.tui.terminal :as term]))

;; --- Styling helpers --------------------------------------------------------

(deftest green-bold-wraps-string-in-sgr
  (testing "green-bold wraps the string in bold green SGR codes"
    ;; given
    (let [s "answer"]
      ;; when
      (let [result (term/green-bold s)]
        ;; then
        (is (= (str term/esc "1;32m" s term/esc "0m")
               result))))))

(deftest print-ansi!-concats-and-prints
  (testing "print-ansi! concatenates all parts and prints them"
    ;; given / when
    (let [out (with-out-str
                (term/print-ansi! "foo" "bar" "baz"))]
      ;; then
      (is (= "foobarbaz" out)))))

(deftest style-answer-respects-ansi-capability
  (testing "style-answer uses green-bold only on ANSI-capable terminals"
    ;; given
    (let [s "value"]
      ;; when / then
      (testing "ANSI-capable: answer is bold green"
        (with-redefs [term/ansi-capable? (constantly true)]
          (is (= (term/green-bold s)
                 (term/style-answer s)))))

      (testing "Non-ANSI: answer is returned unchanged"
        (with-redefs [term/ansi-capable? (constantly false)]
          (is (= s (term/style-answer s))))))))

;; --- OS / environment detection --------------------------------------------

(deftest windows?-matches-os-name-property
  (testing "windows? returns true when os.name contains 'win' (case-insensitive)"
    ;; given
    (let [orig (System/getProperty "os.name")]
      (try
        ;; when
        (System/setProperty "os.name" "Windows 11")
        (is (true? (term/windows?)))

        (System/setProperty "os.name" "Linux")
        (is (false? (term/windows?)))

        ;; then (cleanup)
        (finally
          (if orig
            (System/setProperty "os.name" orig)
            (System/clearProperty "os.name")))))))

(deftest windows-mintty?-true-only-when-windows-and-msys
  (testing "windows-mintty? is true only when both windows? and msys? are true"
    (testing "Windows + MSYS -> true"
      (with-redefs [term/windows? (constantly true)
                    term/msys?    (constantly true)]
        (is (true? (#'term/windows-mintty?)))))

    (testing "Windows without MSYS -> false"
      (with-redefs [term/windows? (constantly true)
                    term/msys?    (constantly false)]
        (is (false? (#'term/windows-mintty?)))))

    (testing "Non-Windows with MSYS (theoretically) -> false"
      (with-redefs [term/windows? (constantly false)
                    term/msys?    (constantly true)]
        (is (false? (#'term/windows-mintty?)))))))

;; --- Terminal type / dumb-terminal? ----------------------------------------

(deftest dumb-terminal?-recognises-dumb-types
  (testing "dumb-terminal? returns truthy for 'dumb' and 'dumb-color', nil otherwise"
    (testing "\"dumb\""
      (with-redefs [term/terminal-type (constantly "dumb")]
        (is (#'term/dumb-terminal?)
            "should be truthy for 'dumb'")))

    (testing "\"dumb-color\""
      (with-redefs [term/terminal-type (constantly "dumb-color")]
        (is (#'term/dumb-terminal?)
            "should be truthy for 'dumb-color'")))

    (testing "\"xterm-256color\""
      (with-redefs [term/terminal-type (constantly "xterm-256color")]
        (is (not (#'term/dumb-terminal?))
            "should be falsey for non-dumb types")))))

;; --- Capability checks ------------------------------------------------------

(deftest ansi-capable?-non-windows-non-dumb
  (testing "ansi-capable? is true on non-dumb, non-Windows terminals"
    ;; given
    (with-redefs [term/dumb-terminal? (constantly false)
                  term/windows?       (constantly false)]
      ;; when / then
      (is (true? (term/ansi-capable?))))))

(deftest ansi-capable?-false-on-dumb-terminal
  (testing "ansi-capable? is false on dumb terminals regardless of OS"
    ;; given
    (with-redefs [term/dumb-terminal? (constantly true)
                  term/windows?       (constantly false)]
      ;; when / then
      (is (false? (term/ansi-capable?))))))

(deftest ansi-capable?-windows-without-msys
  (testing "ansi-capable? is false on plain Windows consoles (no MSYS/mintty)"
    ;; given
    (with-redefs [term/dumb-terminal? (constantly false)
                  term/windows?       (constantly true)
                  term/msys?          (constantly false)]
      ;; when / then
      (is (false? (term/ansi-capable?))))))

(deftest ansi-capable?-windows-with-msys
  (testing "ansi-capable? is true on Windows + MSYS (mintty style terminals)"
    ;; given
    (with-redefs [term/dumb-terminal? (constantly false)
                  term/windows?       (constantly true)
                  term/msys?          (constantly true)]
      ;; when / then
      (is (true? (term/ansi-capable?))))))

(deftest arrow-menu-supported?-mirrors-ansi-capable?-logic
  (testing "arrow-menu-supported? follows the same OS/terminal rules as ansi-capable?"
    ;; given / when / then
    (testing "Non-dumb, Windows+MSYS -> true for both"
      (with-redefs [term/dumb-terminal? (constantly false)
                    term/windows?       (constantly true)
                    term/msys?          (constantly true)]
        (is (true? (term/ansi-capable?)))
        (is (true? (term/arrow-menu-supported?)))))

    (testing "Non-dumb, plain Windows -> false for both"
      (with-redefs [term/dumb-terminal? (constantly false)
                    term/windows?       (constantly true)
                    term/msys?          (constantly false)]
        (is (false? (term/ansi-capable?)))
        (is (false? (term/arrow-menu-supported?)))))))

;; --- Cursor / line helpers --------------------------------------------------

(deftest save-cursor!-respects-ansi-capability
  (testing "save-cursor! emits ESC[s only when ANSI is supported"
    ;; given / when / then
    (testing "ANSI-capable"
      (with-redefs [term/ansi-capable? (constantly true)]
        (let [out (with-out-str (term/save-cursor!))]
          (is (= (str term/esc "s") out)))))

    (testing "Non-ANSI"
      (with-redefs [term/ansi-capable? (constantly false)]
        (let [out (with-out-str (term/save-cursor!))]
          (is (= "" out)))))))

(deftest restore-cursor!-respects-ansi-capability
  (testing "restore-cursor! emits ESC[u only when ANSI is supported"
    ;; given / when / then
    (testing "ANSI-capable"
      (with-redefs [term/ansi-capable? (constantly true)]
        (let [out (with-out-str (term/restore-cursor!))]
          (is (= (str term/esc "u") out)))))

    (testing "Non-ANSI"
      (with-redefs [term/ansi-capable? (constantly false)]
        (let [out (with-out-str (term/restore-cursor!))]
          (is (= "" out)))))))

(deftest clear-line!-respects-ansi-capability
  (testing "clear-line! emits ESC[2K only when ANSI is supported"
    ;; given / when / then
    (testing "ANSI-capable"
      (with-redefs [term/ansi-capable? (constantly true)]
        (let [out (with-out-str (term/clear-line!))]
          (is (= (str term/esc "2K") out)))))

    (testing "Non-ANSI"
      (with-redefs [term/ansi-capable? (constantly false)]
        (let [out (with-out-str (term/clear-line!))]
          (is (= "" out)))))))

(deftest rewrite-prev-line!-emits-expected-ansi-sequence
  (testing "rewrite-prev-line! moves up, clears the line, and writes the new text"
    ;; given
    (let [s "New answer"]
      ;; when
      (with-redefs [term/ansi-capable? (constantly true)]
        (let [out (with-out-str
                    (term/rewrite-prev-line! s))]
          ;; then
          (is (= (str
                  term/esc "1A"    ; move cursor up one line
                  "\r"
                  term/esc "2K"    ; clear line
                  s "\n")
                 out)))))))

(deftest rewrite-prev-line!-does-nothing-when-non-ansi
  (testing "rewrite-prev-line! prints nothing when ANSI is not supported"
    ;; given / when
    (with-redefs [term/ansi-capable? (constantly false)]
      (let [out (with-out-str
                  (term/rewrite-prev-line! "ignored"))]
        ;; then
        (is (= "" out))))))
