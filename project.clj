(defproject robot "0.2.1-SNAPSHOT"
  :description "a magical creation powered by a few unorthodox parts"
  :url "http://github.com/zaxtax/robot"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  ;; TODO: this probably belings in zaxtax's java-aws-mturk project
  :repositories [["jboss" "https://repository.jboss.org/nexus/content/repositories/thirdparty-uploads"]
                 ["jboss2" "https://repository.jboss.org/nexus/content/repositories/thirdparty-releases"]]

  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojars.zaxtax/java-aws-mturk "1.6.2"]

                 ;; TODO: this probably belings in zaxtax's java-aws-mturk project
                 ;[org.apache.commons/not-yet-commons-ssl "0.3.7"]
                 ;[commons-httpclient "3.1"]

                 [clojure-opennlp "0.2.0"]
                 [log4j/log4j "1.2.16"]
                 [hiccup "1.0.2"]
                 [org.clojure/data.xml "0.0.7"]])
