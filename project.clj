(defproject datahike-server "0.1.0-SNAPSHOT"
  :description "Datahike REST service"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [io.replikativ/datahike "0.2.2-SNAPSHOT"]
                 [io.replikativ/konserve "0.5.1"]
                 [clojusc/protobuf "3.5.1-v1.1"]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-jetty-adapter "1.6.3"]
                 [metosin/reitit "0.4.2"]
                 [ring-cors "0.1.13"]
                 [mount "0.1.16"]]
  :source-paths ["src/clj"]
  :main datahike-server.core
  :aot [datahike-server.core]
  :repl-options {:init-ns datahike-server.core})
