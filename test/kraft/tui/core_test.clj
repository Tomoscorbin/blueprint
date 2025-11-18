(ns kraft.tui.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [kraft.tui.core :as tui]
   [kraft.tui.menu :as menu]))

(deftest ask-project-name!-returns-read-line
  (testing "ask-project-name! returns whatever read-line provides"
    (with-redefs [clojure.core/read-line (fn [] "my-project")
                  clojure.core/print    (fn [& _] nil)
                  clojure.core/flush    (fn [] nil)]
      (is (= "my-project"
             (tui/ask-project-name!))))))

(deftest ask-databricks-host!-returns-read-line
  (testing "ask-databricks-host! returns whatever read-line provides"
    (with-redefs [clojure.core/read-line (fn [] "dbc-xyz.cloud.databricks.com")
                  clojure.core/print    (fn [& _] nil)
                  clojure.core/flush    (fn [] nil)]
      (is (= "dbc-xyz.cloud.databricks.com"
             (tui/ask-databricks-host!))))))

(deftest choose-ci-provider!-delegates-to-menu-and-returns-selected-key
  (testing "choose-ci-provider! calls menu/create-menu! with the CI options and returns the selected key"
    (let [menu-calls (atom [])]
      (with-redefs [menu/create-menu!
                    (fn [options]
                      (swap! menu-calls conj options)
                      :azure)                   ;; pretend user chose :azure
                    clojure.core/print (fn [& _] nil)
                    clojure.core/flush (fn [] nil)]
        (let [result (tui/choose-ci-provider!)]
          (is (= :azure result)
              "returns whatever key menu/create-menu! returns")
          (is (= [[[:github "GitHub"]
                   [:azure  "Azure DevOps"]]]
                 @menu-calls)
              "passes the expected options into menu/create-menu!"))))))

(deftest choose-project-type!-delegates-to-menu-and-returns-selected-key
  (testing "choose-project-type! calls menu/create-menu! with the project type options and returns the selected key"
    (let [menu-calls (atom [])]
      (with-redefs [menu/create-menu!
                    (fn [options]
                      (swap! menu-calls conj options)
                      :dabs)                    ;; pretend user chose :dabs
                    clojure.core/print (fn [& _] nil)
                    clojure.core/flush (fn [] nil)]
        (let [result (tui/choose-project-type!)]
          (is (= :dabs result)
              "returns whatever key menu/create-menu! returns")
          (is (= [[[:python-lib "Python Library"]
                   [:dabs       "Databricks Asset Bundle"]]]
                 @menu-calls)
              "passes the expected options into menu/create-menu!"))))))
