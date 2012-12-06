# robot

This is still a work in progress

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
