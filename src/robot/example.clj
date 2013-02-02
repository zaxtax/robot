(use 'robot.core)

(import 'com.amazonaws.mturk.service.axis.RequesterService)
(defn image-questions [] 0)

(defn simple-question []
  (let [service (start-service)
  hit (create-hit service
      "Answer the question"
      "This is a test HIT"
      0.05
      (RequesterService/getBasicFreeTextQuestion
       "What is the current time where you live?")
      2)]
    (fn [] (retrieve-submissions service (.getHITId hit)))))

;;(def s (simple-question))