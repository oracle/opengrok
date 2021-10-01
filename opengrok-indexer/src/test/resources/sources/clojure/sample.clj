;;   Random Clojure to test the Analyzer
;;   @author Farid Zakaria

(ns opengrok
    "This is a sample test file that will be used by the ClojureAnalyzerFactoryTest")

;; Calculate the power set
(defn power-set [coll]
    (reduce (fn [sols a]
                (clojure.set/union (set (map #(conj % a) sols)) sols)) #{#{}} coll))

;; a private function
;; Calculate the power set
(defn- power-set-private [coll]
       "This is the power-set API but private"
      (reduce (fn [sols a]
                  (clojure.set/union (set (map #(conj % a) sols)) sols)) #{#{}} coll))

(defstruct author :first :last)

;constant definition
(def author-first-name "Farid")
(def author-last-name "Zakaria")
(def Farid (struct author author-first-name author-last-name))