(ns kraft.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [kraft.core :as core]
   [kraft.plan :as plan]
   [kraft.exec :as exec]
   [kraft.tui.core :as tui]))

(deftest prompt-answers!-for-python-lib-does-not-ask-host
  (testing "prompt-answers! does not request Databricks host for :python-lib projects"
    (let [host-called? (atom false)]
      (with-redefs [tui/ask-project-name!   (fn [] "my-lib")
                    tui/choose-ci-provider! (fn [] :github)
                    tui/choose-project-type! (fn [] :python-lib)
                    tui/ask-databricks-host! (fn []
                                               (reset! host-called? true)
                                               "should-not-be-used")]
        (let [answers (#'core/prompt-answers!)]
          (is (= {:project-name "my-lib"
                  :ci-provider  :github
                  :project-type :python-lib}
                 answers)
              "only base keys are present for :python-lib")
          (is (false? @host-called?)
              "ask-databricks-host! must not be called for :python-lib"))))))

(deftest prompt-answers!-for-dabs-asks-host
  (testing "prompt-answers! requests Databricks host and includes it for :dabs projects"
    (let [host-called? (atom false)]
      (with-redefs [tui/ask-project-name!   (fn [] "my-dabs-project")
                    tui/choose-ci-provider! (fn [] :azure)
                    tui/choose-project-type! (fn [] :dabs)
                    tui/ask-databricks-host! (fn []
                                               (reset! host-called? true)
                                               "dbc-xyz.cloud.databricks.com")]
        (let [answers (#'core/prompt-answers!)]
          (is (= {:project-name "my-dabs-project"
                  :ci-provider  :azure
                  :project-type :dabs
                  :hostname     "dbc-xyz.cloud.databricks.com"}
                 answers)
              "hostname is included for :dabs projects")
          (is (true? @host-called?)
              "ask-databricks-host! must be called for :dabs"))))))

(deftest generate-project!-plans-layout-and-executes
  (testing "generate-project! wires plan-layout, template-data, and exec/execute! correctly"
    (let [layout-calls   (atom [])
          template-calls (atom [])
          exec-calls     (atom [])]
      (with-redefs [plan/plan-layout
                    (fn [answers]
                      (swap! layout-calls conj answers)
                      {:layout :planned})

                    plan/template-data
                    (fn [answers]
                      (swap! template-calls conj answers)
                      {:data :templated})

                    exec/execute!
                    (fn [layout data]
                      (swap! exec-calls conj [layout data]))]

        (let [answers {:project-name "demo"
                       :ci-provider  :github
                       :project-type :python-lib}]
          (#'core/generate-project! answers)

          (is (= [answers] @layout-calls)
              "plan-layout is called once with the original answers")

          (is (= [answers] @template-calls)
              "template-data is called once with the original answers")

          (is (= [[{:layout :planned} {:data :templated}]]
                 @exec-calls)
              "exec/execute! receives the planned layout and template data"))))))
