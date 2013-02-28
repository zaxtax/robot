(use 'robot.core)

(import 'com.amazonaws.mturk.service.axis.RequesterService)
(defn image-questions [] 0)

(defn simple-question []
  (let [service (start-service)
  hit (create-hit service
      "Answer the question for me!"
      "This is a test HIT, yo!"
      0.05
      (RequesterService/getBasicFreeTextQuestion
       "What is the current time in Century City, CA?")
      2)]
    (println "Created HIT" (.getHITId hit))
    (fn [] (retrieve-submissions service (.getHITId hit)))))

;;(def s (simple-question))