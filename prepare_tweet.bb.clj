#!/usr/bin/env bb
(ns prepare-tweet
  (:require
   [clojure.string :as string]
   [cheshire.core :as json])
  (:import [java.time LocalDate]
           [java.time.format DateTimeFormatter]))

(def date-pattern (DateTimeFormatter/ofPattern "yyyy-MM-dd"))

(def localization
  {:he-IL {"Jan"    "ינואר"
           "Feb"    "פברואר"
           "Mar"    "מרץ"
           "Apr"    "אפריל"
           "May"    "מאי"
           "Jun"    "יוני"
           "Jul"    "יולי"
           "Aug"    "אוגוסט"
           "Sep"    "ספטמבר"
           "Oct"    "אוקטובר"
           "Nov"    "נובמבר"
           "Dec"    "דצמבר"
           "Mon"    "שני"
           "Tue"    "שלישי"
           "Wed"    "רביעי"
           "Thu"    "חמישי"
           "Fri"    "שישי"
           "Sat"    "שבת"
           "Sun"    "ראשון"
           "tweet"  "מפלס המים בים המלח שנמדד ביום %s ה-%s ל%s %s עומד על %.2f מטרים מתחת לפני הים"}})

(defn localized
  [word lang]
  (get-in localization [lang word] word))

(defn format-date
  [^String date pattern]
  (let [d (LocalDate/parse date date-pattern)]
    (.format d (DateTimeFormatter/ofPattern pattern))))

;;; https://github.com/clojure-cookbook/clojure-cookbook/blob/master/01_primitive-data/1-29_comparing-dates.asciidoc#comparing-dates
(defn filter-newer-records
  [records ^String date]
  (let [d (LocalDate/parse date date-pattern)]
    (filter
     (fn [record]
       (let [record-date (LocalDate/parse (:date record) date-pattern)]
         (pos? (compare record-date d))))
     records)))

(defn format-tweet
  [{:keys [date level]} lang]
  (let [day   (format-date date "dd")
        week  (format-date date "EEE")
        year  (format-date date "YYY")
        month (format-date date "MMM")
        tweet (localized "tweet" lang)]
    (format tweet
            (localized week lang)
            day
            (localized month lang)
            year
            (* -1 level))))

;;; build tweet message: get latest tweet date, and check if there
;;; is newer message
(let [in-args (string/split (first *command-line-args*) #" ")
      latest  (slurp (second in-args))
      content (json/parse-string (slurp (first in-args)) true)
      newer   (not-empty (filter-newer-records content latest))]
  (when newer
    (let [record (first newer)]
      (println (format-tweet record :he-IL))
      (spit (second in-args) (string/trim (:date record)) :append false)))
  (System/exit 0))