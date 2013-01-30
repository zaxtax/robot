(ns robot.core
  (:import (com.amazonaws.mturk.requester HIT)
           (com.amazonaws.mturk.service.axis RequesterService)
           (com.amazonaws.mturk.util PropertiesClientConfig)
           (com.amazonaws.mturk.dataschema QuestionFormAnswers)
           (com.amazonaws.mturk.dataschema QuestionFormAnswersType)
           (com.amazonaws.mturk.requester Assignment)
           (com.amazonaws.mturk.requester AssignmentStatus)
           (org.apache.commons.beanutils BeanUtils))
  (:use [hiccup.core])
  (:require [clojure.java.io]
            [clojure.data.xml :as xml]
            [clojure.string :as str]))


(def ^:private ^:dynamic service-atom (atom nil))

(defn mturk!
  "Sets your authentication with Amazon Mechanical Turk.
   Expects credentials to be in resources/mturk.properties"
  []
  (let [client-config (new PropertiesClientConfig "resources/mturk.properties")]
    (reset! service-atom (new RequesterService client-config))))

(defn service []
  (if-let [service @service-atom]
    service
    (throw (IllegalArgumentException. "Your MTurk service must be initialized using 'mturk!'"))))

(defn assignment-m
  "Returns a hash-map built from the data in assignment a, which is
   expected to be a com.amazonaws.mturk.requester.Assignment."
  [a]
  (let [answer-xml (.getAnswer a)]
    {:hit-id     (.getHITId a)
     :answer-xml answer-xml
     :answer     (xml/parse-str answer-xml)
     :status     (.getAssignmentStatus a)
     :submitted  (.getSubmitTime a)
     :worker     (.getWorkerId a)}))

(defn get-assignments
  "Fetches and returns assignments for the specified hit-id. Returns
   a sequence of hash-maps."
  [hit-id]
  (map assignment-m (.getAllAssignmentsForHIT (service) hit-id)))

(defn has-enough-funds? [service])

(defn create-hit [service title desc price question n-answers]
  (let [hit (.createHIT service
      title
      desc
      price
      question
      n-answers)]
    hit))

(defn retrieve-all-hits [service]
  (into [] (.searchAllHITs service)))

(defn clear-hits [service]
  (map (fn [x] (.disableHIT service (.getHITId x)))
       (retrieve-all-hits service)))

(defn approve-assignment [service assign-id]
  (.approveAssignment service assign-id "Good job!"))

(defn retrieve-submissions [service hit-id]
  (for [assign (into [] (.getAllAssignmentsForHIT service hit-id))]
    (if (= (AssignmentStatus/Submitted)
     (.getAssignmentStatus assign))
      (do (approve-assignment service (.getAssignmentId assign))
    (.getAnswer assign)))))

(defn start-service []
  (let [client-config (new PropertiesClientConfig "resources/mturk.properties")
  service (new RequesterService client-config)]
  service))

(defn image [url]
  (html
   [:Binary
    [:MimeType
     [:Type "image"]
     [:SubType (last (str/split url #"\."))]]
    [:DataURL url]
    [:AltText (last (str/split url #"\/"))]]))

(defn list-question [question choices]
  (let [prefix "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"]
    (str prefix
   (html
    [:QuestionForm {:xmlns "http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2005-10-01/QuestionForm.xsd"}
     [:Question
      [:QuestionIdentifier 1]
      [:QuestionContent [:Text question]]
      [:AnswerSpecification
       [:SelectionAnswer
        [:MinSelectionCount 1]
        [:MaxSelectionCount 1]
        [:StyleSuggestion "radiobutton"]
        [:Selections
         (for [x choices]
     [:Selection
      [:SelectionIdentifier x]
      [:Text x]])]]]]])
   )))
