(ns robot.core
  (:import (com.amazonaws.mturk.requester HIT
                                          Assignment
                                          AssignmentStatus
                                          NotificationTransport
                                          NotificationSpecification
                                          EventType)
           (com.amazonaws.mturk.service.axis RequesterService)
           (com.amazonaws.mturk.util PropertiesClientConfig)
           (com.amazonaws.mturk.dataschema QuestionFormAnswers)
           (com.amazonaws.mturk.dataschema QuestionFormAnswersType)

           (org.apache.commons.beanutils BeanUtils))
  (:use [hiccup.core])
  (:require [clojure.java.io]
            [clojure.data.xml :as xml]
            [clojure.string :as str]))

;; The version of the Notification API WSDL/schema
(def WSDL-SCHEMA-VER "2006-05-05")

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

(defn- val->kwd [o]
  (-> o .getValue keyword))

(defn caltime
  "Returns time in millis from Calendar cal, or nil if cal is nil."
  [cal]
  (when cal (.getTimeInMillis cal)))

;;TODO: handle nulls, like timestamps
(defn assignment-m
  "Returns a hash-map built from the data in assignment a, which is
   expected to be a com.amazonaws.mturk.requester.Assignment."
  [a]
  (let [answer-xml (.getAnswer a)]
    {:hit-id           (.getHITId a)
     :accepted-at      (caltime  (.getAcceptTime a))
     :answer-xml       answer-xml
     :answer           (xml/parse-str answer-xml)
     :approved-at      (caltime (.getApprovalTime a))
     :assignment-id    (.getAssignmentId a)
     :status           (val->kwd (.getAssignmentStatus a))
     :auto-approval-at (caltime (.getAutoApprovalTime a))
     :deadline         (caltime (.getDeadline a))
     :rejected-at      (caltime (.getRejectionTime a))
     :submitted-at     (caltime (.getSubmitTime a))
     ;;TODO: .getTypeDesc
     :worker-id        (.getWorkerId a)
     :requester-feedback (.getRequesterFeedback a)}))

(defn hit-m [h]
  {:hit-id           (.getHITId h)
   :duration-secs    (.getAssignmentDurationInSeconds h)
   :created-at       (.getTimeInMillis (.getCreationTime h))
   :description      (.getDescription h)
   :expiration       (.getTimeInMillis (.getExpiration h))
   :group-id         (.getHITGroupId h)
   :layout-id        (.getHITLayoutId h)
   :review-status    (val->kwd (.getHITReviewStatus h))
   :status           (val->kwd (.getHITStatus h))
   :type-id          (.getHITTypeId h)
   :keywords         (.getKeywords h)
   :max-assignments  (.getMaxAssignments h)
   ;; TODO: .getQualificationRequirement
   :question-xml     (.getQuestion h)
   :reward           (.floatValue (.getAmount (.getReward h)))
   :title            (.getTitle h)
   :auto-approval-delay-secs  (.getAutoApprovalDelayInSeconds h)
   :requester-annotation      (.getRequesterAnnotation h)
   :num-assignments-available (.intValue (.getNumberOfAssignmentsAvailable h))
   :num-assignments-completed (.intValue (.getNumberOfAssignmentsCompleted h))
   :num-assignments-pending   (.intValue (.getNumberOfAssignmentsPending h))})

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

(defn set-notification [hit-type-id notification active]
  (.setHITTypeNotification (service) hit-type-id notification active))

;; convenience function to create a Java array of EventTypes
;; TODO: please tell me there's a better way!
(defn event-types [etypes-in]
  (let [etypes-out (make-array EventType (count etypes-in))]
    (doseq [i (range (count etypes-in))]
      (aset etypes-out i (get etypes-in i)))
    etypes-out))

(defn activate-sqs-notification
  "Activates SQS notification for the specified hit-type-id and your
   SQS queue url. You will need to setup and configure your AWS SQS.
     http://docs.aws.amazon.com/AWSMechTurk/latest/AWSMturkAPI/ApiReference_NotificationReceptorAPI_SQSTransportArticle.html

   TODO: currently only one hard-coded event type"
  [hit-type-id queue-url]
  (let [notify (NotificationSpecification.
                queue-url
                NotificationTransport/SQS
                WSDL-SCHEMA-VER
                (event-types [EventType/HITReviewable]))]
    (set-notification hit-type-id notify true)))

(defn get-all-hits []
  (map hit-m (.searchAllHITs (service))))

(defn get-reviewable-hits []
  (.getAllReviewableHITs (service) "type"))

(defn clear-hits []
  (map (fn [x] (.disableHIT (service) (.getHITId x)))
       (get-all-hits (service))))

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
