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
	    [clojure.string :as str]))

(defn load-props
  [file-name]
  (with-open [^java.io.Reader reader (clojure.java.io/reader file-name)] 
    (let [props (java.util.Properties.)]
      (.load props reader)
      (into {} (for [[k v] props] [(read-string k) (read-string v)])))))

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

	      