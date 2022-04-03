(ns build
  (:require
    [borkdude.gh-release-artifact :as gh]
    [clojure.tools.build.api :as b]
    [deps-deploy.deps-deploy :as dd])
  (:import
    [java.nio.file Paths]
    [com.google.cloud.tools.jib.api Jib Containerizer RegistryImage TarImage]
    [com.google.cloud.tools.jib.api.buildplan AbsoluteUnixPath Port]))

(def lib 'io.replikativ/datahike-server)
(def version (format "0.1.%s" (b/git-count-revs nil)))
(def current-commit (gh/current-commit))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-path (format "target/%s-%s.jar" (name lib) version))
(def uber-file (format "%s-%s-standalone.jar" (name lib) version))
(def uber-path (format "target/%s" uber-file))
(def image (format "docker.io/replikativ/datahike-server:%s" version))
(def latest-image (format "docker.io/replikativ/datahike-server:latest"))

(defn get-version
  [_]
  (println version))

(defn clean
  [_]
  (b/delete {:path "target"}))

(defn compile
  [_]
  (b/javac {:src-dirs ["java"]
            :class-dir class-dir
            :basis basis
            :javac-opts ["-source" "8" "-target" "8"]}))

(defn jar
  [_]
  (compile nil)
  (b/write-pom {:class-dir class-dir
                :src-pom "./template/pom.xml"
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-path}))

(defn uber
  [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-path
           :basis basis
           :main 'datahike-server.core}))

(defn deploy
  "Don't forget to set CLOJARS_USERNAME and CLOJARS_PASSWORD env vars."
  [_]
  (dd/deploy {:installer :remote :artifact jar-path
              :pom-file (b/pom-path {:lib lib :class-dir class-dir})}))

(defn release
  [_]
  (-> (gh/overwrite-asset {:org "replikativ"
                           :repo (name lib)
                           :tag version
                           :commit current-commit
                           :file uber-path
                           :content-type "application/java-archive"})
      :url
      println))

(defn install
  [_]
  (clean nil)
  (jar nil)
  (b/install {:basis (b/create-basis {})
              :lib lib
              :version version
              :jar-file jar-path
              :class-dir class-dir}))

(defn deploy-image
  [{:keys [docker-login docker-password]}]
  (if-not (and docker-login docker-password)
    (println "Docker credentials missing.")
    (let [container-builder (-> (Jib/from "gcr.io/distroless/java17-debian11")
                                (.addLayer [(Paths/get uber-path (into-array String[]))] (AbsoluteUnixPath/get "/"))
                                (.setProgramArguments [(format "/%s" uber-file)])
                                (.addExposedPort (Port/tcp 3000)))]
       (.containerize
         container-builder
         (Containerizer/to
           (-> (RegistryImage/named image)
               (.addCredential (str docker-login) (str docker-password)))))
       (.containerize
         container-builder
         (Containerizer/to
           (-> (RegistryImage/named latest-image)
               (.addCredential (str docker-login) (str docker-password)))))))
  (println "Deployed new image to Docker Hub: " image))

(comment
  (def docker-login "")
  (def docker-password "")

  (b/pom-path {:lib lib :class-dir class-dir})
  (clean nil)
  (compile nil)
  (jar nil)
  (uber nil)
  (deploy-image {:docker-login docker-login
                 :docker-password docker-password})
  (deploy nil)
  (release nil)
  (install nil))
