(defproject io.replikativ/datahike-server "0.1.0-SNAPSHOT"
  :description "Datahike REST service"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1" :scope "provided"]
                 [io.replikativ/datahike "0.3.2"]
                 [buddy/buddy-auth "2.2.0"]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-jetty-adapter "1.6.3"]
                 [metosin/reitit "0.5.10"]
                 [ring-cors "0.1.13"]
                 [com.taoensso/timbre "4.10.0"]
                 [environ "1.2.0"]
                 [mount "0.1.16"]]
  :profiles {:dev {:dependencies [[clj-http "3.10.1"]]}}
  :source-paths ["src/clj"]
  :main datahike-server.core
  :aot [datahike-server.core]
  :repl-options {:init-ns datahike-server.core}
  :uberjar-name "datahike-server-standalone.jar"
  :plugins [[lein-cljfmt "0.6.8"]])
