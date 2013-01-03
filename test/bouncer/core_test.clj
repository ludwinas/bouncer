(ns bouncer.core-test
  (:use clojure.test)
  (:require [bouncer.core :as core]))
(require '[bouncer.core :as core] :reload)
(deftest validations
  (testing "Required validations"
    (is (not (core/valid? {}
                          :name core/required)))
    (is (not (core/valid? {:name ""}
                          :name core/required)))
    (is (not (core/valid? {:name nil}
                          :name core/required)))
    (is (core/valid? {:name "Leo"}
                     :name core/required)))

  (testing "Number validations"
    ;; map entries are optional by default...
    (is (core/valid? {}
                     :age core/number))
    ;;unless otherwise specified
    (is (not (core/valid? {}
                          :age [core/required core/number])))
    (is (not (core/valid? {:age "invalid"}
                          :age [core/number core/positive])))
    (is (core/valid? {:age nil}
                     :age core/number))
    (is (core/valid? {:age 10}
                     :age core/number))))

(def map-no-street {:address {:street nil :country "Brazil"}})
(def map-with-street (assoc-in map-no-street [:address :street]
                               "Rock 'n Roll Boulevard"))

(deftest nested-maps
  (testing "nested validations"
    (is (not (core/valid? map-no-street
                          :address core/required
                          [:address :street] core/required)))
    
    (is (not (core/valid? map-no-street
                          [:address :street] core/required)))
    
    (is (core/valid? map-with-street
                     :address core/required
                     [:address :street] core/required))
    
    (is (core/valid? map-with-street
                          [:address :street] core/required))

    (is (not (core/valid? {}
                          [:address :street] core/required))))


  (testing "optional nested validations"
    (is (core/valid? {:passport {:issue-year 2012}}
                     [:passport :issue-year] core/number))

    (is (core/valid? {:passport {:issue-year nil}}
                     [:passport :issue-year] core/number))
    
    (is (not (core/valid? {:passport {:issue-year nil}}
                          [:passport :issue-year] [core/required core/number])))))


(defn first-error-for [key validation-result]
  (-> validation-result (first) key (first)))

(deftest validation-messages
  (testing "default messages"
    (is (= "name must be present"
           (first-error-for :name
                            (core/validate {}
                                           :name core/required))))

    (is (= "age must be present"
           (first-error-for :age
                            (core/validate {}
                                           :age core/required))))
    (is (= "age must be a number"
           (first-error-for :age
                            (core/validate {:age ""}
                                           :age core/number))))

    (is (= "age must be a positive number"
           (first-error-for :age
                            (core/validate {:age -7}
                                           :age core/positive))))

    (is (= {
            :age '("age must be a number" "age must be present")
            :name '("name must be present")
            }
           (first (core/validate {:age ""}
                                 :name core/required
                                 :age [core/required core/number])))))

  (testing "custom messages"
    (is (= {
            :age '("Idade deve ser maior que zero")
            :year '("Ano deve ser um numero" "Ano eh obrigatorio")
            :name '("Nome eh obrigatorio")
            }
           (first (core/validate {:age -1 :year ""}
                                 :name (core/required :message "Nome eh obrigatorio")
                                 :year [(core/required :message "Ano eh obrigatorio")
                                        (core/number :message "Ano deve ser um numero")]
                                 :age (core/positive :message "Idade deve ser maior que zero"))
                  )))))

(deftest validation-result
  (testing "invalid results"
    (let [[result map] (core/validate {:age -1 :year ""}
                                      :name core/required
                                      :year [core/required core/number]
                                      :age core/positive)]
      (is (= result (:errors map)))))


  (testing "valid results"
    (let [[result map] (core/validate {:name "Leo"}
                                      :name core/required)]
      (is (true? (and (empty? result)
                      (nil? (:errors map))))))))


(deftest array-validations
  (let [valid-map   {:name "Leo" :pets [{:name "Aragorn"} {:name "Gandalf"}]}
        invalid-map {:name "Leo" :pets [{:name nil} {:name "Gandalf"}]}]
    
    (testing "nested colls"
      (is (core/valid? valid-map
                       :pets (core/every #(not (nil? (:name %))))))


      
      (is (not (core/valid? invalid-map
                            :pets (core/every #(not (nil? (:name %))))))))

    (testing "default messages for nested colls"
      (let [[result map] (core/validate invalid-map
                                        :pets (core/every #(not (nil? (:name %)))))]
        (is (= "All items in pets must satisfy the predicate"
               (-> result :pets (first))))))

    (testing "custom messages for nested colls"
      (let [[result map] (core/validate invalid-map
                                        :pets (core/every #(not (nil? (:name %)))
                                                          :message "All pets must have names"))]
        (is (= "All pets must have names"
               (-> result :pets (first)))))))
  

  (testing "deep nested coll"
    (is (core/valid? {:name "Leo"
                      :address {:current { :country "Australia"}
                                :past [{:country "Spain"} {:country "Brasil"}]}}
                     [:address :past] (core/every #(not (nil? (:country %))))))
    
    (is (not (core/valid? {:name "Leo"
                           :address {:current { :country "Australia"}
                                     :past [{:country "Spain"} {:country nil}]}}
                          [:address :past] (core/every #(not (nil? (:country %)))))))))
;;(require '[bouncer.core :as core] :reload)
(deftest all-validations
  (testing "all built-in validators"
    (let [errors-map {
                      :age '("age must be a number" "age must be present")
                      :name '("name must be present")
                      :passport {:number '("number must be a positive number")}
                      :address {:past '("All items in past must satisfy the predicate")}
                      }
          invalid-map {:name nil
                       :age ""
                       :passport {:number -7 :issued_by "Australia"}
                       :address {:current { :country "Australia"}
                                 :past [{:country nil} {:country "Brasil"}]}}]
      (is (= errors-map
             (first (core/validate invalid-map
                                   :name core/required
                                   :age [core/required core/number]
                                   [:passport :number] core/positive 
                                   [:address :past] (core/every #(not (nil? (:country %)))))))))))
