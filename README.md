# robot

This is still a work in progress

## Getting Started

Create file `resources/mturk.properties`

In this file insert
```
access_key=YOUR_KEY
secret_key=YOUR_SECRET
service_url=https://mechanicalturk.sandbox.amazonaws.com/?Service=AWSMechanicalTurkRequester
```

## Usage
```clojure
user=> (use 'robot.core)
nil
user=> (defn simple-question []
  (let [service (start-service)
	hit (create-hit service
			"Answer the question"
			"This is a test HIT"
			0.05
			(RequesterService/getBasicFreeTextQuestion
			 "What is the current time where you live?")
			2)]
    (fn [] (retrieve-submissions service (.getHITId hit)))))
```

## License

Copyright © 2012 

Distributed under the Eclipse Public License, the same as Clojure.
