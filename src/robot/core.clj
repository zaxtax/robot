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
  (when o
      (-> o .getValue keyword)))

(defn safe-int [I]
  (when I (.intValue I)))

(defn caltime
  "Returns time in millis from Calendar cal, or nil if cal is nil."
  [cal]
  (when cal (.getTimeInMillis cal)))

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
   :created-at       (caltime (.getCreationTime h))
   :description      (.getDescription h)
   :expiration       (caltime (.getExpiration h))
   :group-id         (.getHITGroupId h)
   :layout-id        (.getHITLayoutId h)
   :review-status    (val->kwd (.getHITReviewStatus h))
   :status           (val->kwd (.getHITStatus h))
   :hit-type-id      (.getHITTypeId h)
   :keywords         (.getKeywords h)
   :max-assignments  (.getMaxAssignments h)
   ;; TODO: .getQualificationRequirement
   :question-xml     (.getQuestion h)
   :reward           (when-let [reward (.getReward h)]
                         (.floatValue (.getAmount reward)))
   :title            (.getTitle h)
   :auto-approval-delay-secs  (.getAutoApprovalDelayInSeconds h)
   :requester-annotation      (.getRequesterAnnotation h)
   :num-assignments-available (safe-int (.getNumberOfAssignmentsAvailable h))
   :num-assignments-completed (safe-int (.getNumberOfAssignmentsCompleted h))
   :num-assignments-pending   (safe-int (.getNumberOfAssignmentsPending h))})

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

(def EVENT-TYPES
  {;;:Ping                 EventType/Ping
   :AssignmentAccepted   EventType/AssignmentAccepted
   :AssignmentAbandoned  EventType/AssignmentAbandoned
   :AssignmentReturned   EventType/AssignmentReturned
   :AssignmentSubmitted  EventType/AssignmentSubmitted
   :HITExpired           EventType/HITExpired
   :HITReviewable        EventType/HITReviewable})

(def ALL-EVENT-TYPES (map first EVENT-TYPES))

(defn event-type-for [kwd]
  (if-let [etype (EVENT-TYPES kwd)]
    etype
    (throw (RuntimeException. (str "Unrecognized event type keyword: " kwd)))))

(defn java-event-types
  "Given types as keywords like :HITReviewable, returns the typed
   Java array required by the SDK for doing things like creating
   a NotificationSpecification."
  [types]
  (into-array EventType (map event-type-for types)))

;; convenience function to create a Java array of EventTypes
;; TODO: please tell me there's a better way!
(defn event-types [etypes-in]
  (let [etypes-out (make-array EventType (count etypes-in))]
    (doseq [i (range (count etypes-in))]
      (aset etypes-out i (EVENT-TYPES (get etypes-in i))))
    etypes-out))

(defn activate-sqs-notification
  "Activates SQS notification for the specified hit-type-id and your
   SQS queue url. You will need to setup and configure your AWS SQS.
     http://docs.aws.amazon.com/AWSMechTurk/latest/AWSMturkAPI/ApiReference_NotificationReceptorAPI_SQSTransportArticle.html

   etypes should be an array of keywords indicating the desired notification event types, e.g.:
     [:AssignmentAccepted :AssignmentSubmitted]

   The variation that takes no etypes will set notification for ALL event types."
  ([hit-type-id queue-url etypes]
      (let [notify (NotificationSpecification.
                    queue-url
                    NotificationTransport/SQS
                    WSDL-SCHEMA-VER
                    (event-types etypes))]
        (set-notification hit-type-id notify true)))
  ([hit-type-id queue-url]
     (activate-sqs-notification hit-type-id queue-url ALL-EVENT-TYPES)))

(defn get-all-hits []
  (map hit-m (.searchAllHITs (service))))

(defn get-all-assignments []
  (mapcat get-assignments (map :hit-id (get-all-hits))))

(defn get-reviewable-hits [hit-type-id]
  (.getAllReviewableHITs (service) hit-type-id))

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

;;
;; Break out all XML building into a different namespace
;;

(def XML-PREFIX "<?xml version=\"1.0\" encoding=\"UTF-8\"?>")

(defn image [url]
  (html
   [:Binary
    [:MimeType
     [:Type "image"]
     [:SubType (last (str/split url #"\."))]]
    [:DataURL url]
    [:AltText (last (str/split url #"\/"))]]))

(defn list-question [question choices]
  (str XML-PREFIX
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
                [:Text x]])]]]]])))

(defn simple-formatted-content-question [formatted-content]
  (str XML-PREFIX
       (html
        [:QuestionForm {:xmlns "http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2005-10-01/QuestionForm.xsd"}
         [:Question
          [:QuestionIdentifier 1]
          [:QuestionContent
           [:FormattedContent
            (format "<![CDATA[%s]]>" formatted-content)]]
          [:AnswerSpecification
           [:FreeTextAnswer
            ;;[:Constraints [:Length {:minLength 1 :maxLength 32}]]
            [:NumberOfLinesSuggestion 1]]]]])))

(defn post-simple-hit
  "Posts a simple HIT to MTurk and returns the HIT data, including :hit-id.

   The instructions will appear above the form field, and can be formatted HTML content."
  [{:keys [title description price instructions max-assignments]}]
  (hit-m
   (create-hit (service)
               title
               description
               price
               (simple-formatted-content-question instructions)
               max-assignments)))
