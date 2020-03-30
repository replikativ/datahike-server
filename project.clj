(defproject datahike-server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [io.replikativ/datahike "0.2.2-SNAPSHOT"]
                 [clojusc/protobuf "3.5.1-v1.1"]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-jetty-adapter "1.6.3"]
                 [metosin/reitit "0.4.2"]
                 [mount "0.1.16"]
                 [mount "0.1.16"]]
  :repl-options {:init-ns datahike-server.core})
