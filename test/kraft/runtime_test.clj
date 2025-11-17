(ns kraft.runtime-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [kraft.runtime :as runtime]))

(deftest resolve-python-version-uses-fixed-lib-version-for-python-lib-projects
  (testing "resolve-python-version returns python-lib version for :python-lib projects"
    (let [calls (atom [])]
      (with-redefs [runtime/runtime-python-version
                    (fn []
                      (swap! calls conj :runtime-python-version)
                      "IGNORED")]
        (let [result (runtime/resolve-python-version {:project-type :python-lib})]
          (is (= "3.14" result)
              "python-lib projects should use the fixed python-lib version")
          (is (empty? @calls)
              "runtime manifest should not be consulted for :python-lib projects"))))))

(deftest resolve-python-version-uses-runtime-for-non-python-lib-projects
  (testing "resolve-python-version delegates to runtime-python-version for non :python-lib projects"
    (with-redefs [runtime/runtime-python-version
                  (fn [] "3.12.3")]
      (is (= "3.12.3"
             (runtime/resolve-python-version {:project-type :dabs}))
          ":dabs projects use the runtime Python version")
      (is (= "3.12.3"
             (runtime/resolve-python-version {:project-type :something-else}))
          "other project types also use the runtime Python version")
      (is (= "3.12.3"
             (runtime/resolve-python-version {}))
          "missing project-type defaults to the runtime Python version"))))

(deftest resolve-requires-python-only-for-dabs-projects
  (testing "resolve-requires-python returns a value only for :dabs projects"
    (with-redefs [runtime/runtime-requires-python
                  (fn [] ">=3.12,<3.13")]
      (is (= ">=3.12,<3.13"
             (runtime/resolve-requires-python {:project-type :dabs}))
          ":dabs projects get the requires-python constraint from the manifest")
      (is (nil?
           (runtime/resolve-requires-python {:project-type :python-lib}))
          "non-dabs projects do not have a requires-python constraint")
      (is (nil?
           (runtime/resolve-requires-python {}))
          "missing project-type also yields no requires-python constraint"))))

(deftest resolve-databricks-runtime-only-for-dabs-projects
  (testing "resolve-databricks-runtime returns a version only for :dabs projects"
    (with-redefs [runtime/runtime-version
                  (fn [] "17.3")]
      (is (= "17.3"
             (runtime/resolve-databricks-runtime {:project-type :dabs}))
          ":dabs projects get the Databricks runtime version from the manifest")
      (is (nil?
           (runtime/resolve-databricks-runtime {:project-type :python-lib}))
          "non-dabs projects do not have a Databricks runtime")
      (is (nil?
           (runtime/resolve-databricks-runtime {}))
          "missing project-type also yields no Databricks runtime"))))
