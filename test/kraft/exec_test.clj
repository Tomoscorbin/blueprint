(ns kraft.exec-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [kraft.exec :as exec]))

(defn- temp-dir []
  ;; Create a temporary directory we can use for file tests.
  (let [f (java.io.File/createTempFile "kraft-exec-" "tmp")]
    (.delete f)
    (.mkdir f)
    f))

(deftest write-empty-file-creates-file-and-parent-directories
  (testing "write-empty-file! creates an empty file and its parent dirs"
    (let [root (temp-dir)
          dest (str (io/file root "nested" "empty.txt"))]
      (#'exec/write-empty-file! dest)
      (let [f (io/file dest)]
        (is (.exists f) "file should exist")
        (is (= "" (slurp f)) "file should be empty")
        (is (.isDirectory (.getParentFile f))
            "parent directory should exist")))))

(deftest render-template-throws-when-resource-missing
  (testing "render-template! throws when the template resource cannot be found"
    (with-redefs [io/resource (fn [_] nil)]
      (is (thrown? clojure.lang.ExceptionInfo
                   (#'exec/render-template! "out.txt" "missing.tpl" {} :test-id))))))

(deftest execute-renders-template-and-writes-file
  (testing "execute! renders a Selmer template with data and writes the result"
    (let [root   (temp-dir)
          dest   (str (io/file root "hello.txt"))
          layout {:hello {:destination dest
                          :source      "templates/test_template.selmer"}}]
      (exec/execute! layout {:name "Jim"})
      (is (= "Hello Jim!" (str/trim (slurp dest)))))))

(deftest execute-writes-empty-files-for-nil-sources
  (testing "execute! creates empty files for layout entries with nil :source"
    (let [root   (temp-dir)
          dest-a (str (io/file root "a.txt"))
          dest-b (str (io/file root "nested" "b.txt"))
          layout {:a {:destination dest-a :source nil}
                  :b {:destination dest-b :source nil}}]
      (exec/execute! layout {:ignored "data"})
      (doseq [d [dest-a dest-b]]
        (let [f (io/file d)]
          (is (.exists f) (str "file should exist: " d))
          (is (= "" (slurp f))
              (str "file should be empty: " d)))))))

(deftest execute-dispatches-to-render-or-empty-based-on-source
  (testing "execute! calls render-template! for non-nil :source and write-empty-file! otherwise"
    (let [calls (atom [])]
      (with-redefs [exec/render-template!
                    (fn [destination source data template-id]
                      (swap! calls conj [:render destination source data template-id]))
                    exec/write-empty-file!
                    (fn [destination]
                      (swap! calls conj [:empty destination]))]
        (let [layout {:tpl   {:destination "out.tpl" :source "templates/demo.tpl"}
                      :empty {:destination "empty.txt" :source nil}}
              data   {:name "demo"}]
          (exec/execute! layout data)
          (is (= [[:render "out.tpl" "templates/demo.tpl" data :tpl]
                  [:empty "empty.txt"]]
                 @calls)))))))
