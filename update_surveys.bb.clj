#!/usr/bin/env bb
(ns update-surveys
  (:require
   [clojure.java.shell :as shell]
   [clojure.edn :as edn]
   [clojure.string :as string]
   [cheshire.core :as json])
  (:import [java.time LocalDate]
           [java.time.format DateTimeFormatter]))

(def in-date-pattern (DateTimeFormatter/ofPattern "dd/MM/yyyy"))
(def out-date-pattern (DateTimeFormatter/ofPattern "yyyy-MM-dd"))
(def data-gov-env (System/getenv "DATA_GOV_RESOURCE"))
(def data-gov-url "https://data.gov.il/api/3/action/datastore_search")

(defn format-date
  [{:keys [date] :as record}]
  (let [d (LocalDate/parse date in-date-pattern)]
    (assoc record :date (.format d out-date-pattern))))

(defn remove-existing-records
  [existing-records api-records]
  (let [dates (set (map :date existing-records))]
    (remove #(contains? dates (:date %)) api-records)))

;;; download new surveys and compare with existing surveys
(let [in-file (first *command-line-args*)
      history (json/parse-string (slurp in-file) true)
      api-url (str data-gov-url "?resource_id=" data-gov-env "&limit=15")
      {:keys [exit err out]} (shell/sh
                              "curl" "-s"
                              "-H" "Accept: application/json"
                              "-H" "Content-Type: application/json"
                              api-url)]
  (if (zero? exit)
    (let [response (-> (string/replace out "תאריך מדידה" "date")
                       (string/replace "מפלס" "level")
                       (json/parse-string true))]
      (if (true? (:success response))
          (let [api-records (->> (:result response)
                                 (:records)
                                 (map #(dissoc % :_id))
                                 (map format-date)
                                 (map (fn [record]
                                        (update record :level #(edn/read-string (string/trim %))))))
                new-records (remove-existing-records history api-records)]
            (if (seq new-records)
              (let [updated-history (sort #(let [d1 (LocalDate/parse (:date %1) out-date-pattern)
                                                 d2 (LocalDate/parse (:date %2) out-date-pattern)]
                                             (.isBefore d2 d1))
                                     (concat new-records history))]
                (spit in-file (json/generate-string updated-history) :append false)
                (println "update water level history with new surveys" new-records))
              (println "no new surveys to update water level history records"))
            (System/exit 0))
        (do
          (println "failed to call" api-url "response" (:error response))
          (System/exit 1))))
    (do
      (println "failed to call" api-url "error" err)
      (System/exit exit))))