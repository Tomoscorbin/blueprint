(ns blueprint.plan-test
  (:require
   [clojure.set :as set]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [blueprint.plan :as plan]
   [blueprint.runtime :as runtime]))

(deftest join-path-delegates-to-io-file
  (testing "join-path builds paths the same way as java.io.File"
    (let [root     "my-project"
          relative "README.md"
          joined   (#'plan/join-path root relative)
          expected "my-project/README.md"]
      (is (= expected joined)))))

(deftest apply-pkg-placeholder-replaces-token-in-destinations
  (testing "apply-pkg-placeholder replaces {pkg} only in :destination values"
    (let [layout {:main {:destination "src/{pkg}/main.py"
                         :source      "templates/main.py.selmer"}
                  :readme {:destination "README.md"
                           :source      "templates/readme.md.selmer"}}
          pkg    "demo_pkg"
          result (#'plan/apply-pkg-placeholder layout pkg)]

      (is (= "src/demo_pkg/main.py"
             (get-in result [:main :destination]))
          "destinations containing {pkg} are updated")
      (is (= "README.md"
             (get-in result [:readme :destination]))
          "destinations without {pkg} are unchanged")
      (is (= (get-in layout [:main :source])
             (get-in result [:main :source]))
          ":source is preserved"))))

(deftest qualify-layout-prefixes-root-on-all-destinations
  (testing "qualify-layout prepends the project root to every :destination"
    (let [root   "my-project"
          layout {:readme {:destination "README.md"
                           :source "templates/readme.md.selmer"}
                  :main   {:destination "src/{pkg}/main.py"
                           :source "templates/main.py.selmer"}}
          qualified (#'plan/qualify-layout layout root)]

      (is (= (str (io/file root "README.md"))
             (get-in qualified [:readme :destination]))
          "README destination is qualified")
      (is (= (str (io/file root "src/{pkg}/main.py"))
             (get-in qualified [:main :destination]))
          "main destination is qualified")
      (is (= (get-in layout [:readme :source])
             (get-in qualified [:readme :source]))
          ":source is preserved"))))

(deftest choose-ci-layout-adds-github-files
  (testing "choose-ci-layout returns GitHub CI entries for :github"
    (let [answers  {:ci-provider :github}
          ci-layout (#'plan/choose-ci-layout answers)]
      (is (= #{:github-ci :github-bump}
             (set (keys ci-layout)))))))

(deftest choose-ci-layout-adds-azure-files
  (testing "choose-ci-layout returns Azure CI entry for :azure"
    (let [answers  {:ci-provider :azure}
          ci-layout (#'plan/choose-ci-layout answers)]
      (is (= #{:azure-ci :azure-bump}
             (set (keys ci-layout)))))))

(deftest choose-project-files-adds-dabs-files
  (testing "choose-project-files returns Databricks bundle files for :dabs"
    (let [answers {:project-type :dabs}
          project-layout (#'plan/choose-project-files answers)]
      (is (= #{:databricks-yaml :sample-job}
             (set (keys project-layout)))))))

(deftest compose-layout-merges-base-ci-and-project-files
  (testing "compose-layout merges base, CI and project-type-specific entries"
    (let [answers    {:ci-provider  :github
                      :project-type :dabs}
          base-keys  (set (keys (#'plan/base-layout)))
          layout     (#'plan/compose-layout answers)
          layout-keys (set (keys layout))]
      (is (set/subset? base-keys layout-keys)
          "result must include all base layout entries")
      (is (every? layout-keys [:github-ci :github-bump :databricks-yaml :sample-job])
          "result must include CI + project-type specific entries"))))

(deftest plan-layout-qualifies-all-destinations
  (testing "plan-layout qualifies every destination with the project root"
    (let [answers {:project-name "my_project"
                   :ci-provider  :github
                   :project-type :dabs}
          layout  (plan/plan-layout answers)
          readme  (get-in layout [:readme :destination])
          main    (get-in layout [:main :destination])]

      (is (= (str (io/file "my_project" "README.md"))
             readme)
          "README destination is qualified")
      (is (= (str (io/file "my_project" "src/my_project/main.py"))
             main)
          "main destination is qualified"))))

(deftest collect-additional-details-delegates-to-runtime-resolvers
  (testing "collect-additional-details calls all runtime resolvers and returns their values"
    (let [answers {:some "input"}
          calls   (atom [])]
      (with-redefs [runtime/resolve-python-version
                    (fn [a]
                      (swap! calls conj [:python-version a])
                      "3.12.3")
                    runtime/resolve-requires-python
                    (fn [a]
                      (swap! calls conj [:requires-python a])
                      ">=3.12,<3.13")
                    runtime/resolve-databricks-runtime
                    (fn [a]
                      (swap! calls conj [:databricks-runtime a])
                      "Databricks Runtime 17.3 LTS")]
        (let [result (#'plan/collect-additional-details answers)]

          (is (= {:python_version_value  "3.12.3"
                  :requires_python_value ">=3.12,<3.13"
                  :databricks_runtime    "Databricks Runtime 17.3 LTS"}
                 result))
          (is (= [[:python-version answers]
                  [:requires-python answers]
                  [:databricks-runtime answers]]
                 @calls)
              "resolvers are called once each with the original answers"))))))
