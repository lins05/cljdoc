(ns cljdoc.server.api
  (:require [cljdoc.analysis.service :as analysis-service]
            [cljdoc.server.ingest :as ingest]
            [cljdoc.server.build-log :as build-log]
            [cljdoc.util :as util]
            [cljdoc.util.repositories :as repositories]
            [clojure.tools.logging :as log]))

(defn analyze-and-import-api!
  [{:keys [analysis-service storage build-tracker]}
   {:keys [project version jar pom build-id]}]
  ;; More work is TBD here in order to pass the configuration
  ;; received from a users Git repository into the analysis service
  ;; https://github.com/cljdoc/cljdoc/issues/107
  (let [ana-v    (:analyzer-version analysis-service)
        ana-resp (analysis-service/trigger-build
                  analysis-service
                  {:project project
                   :version version
                   :jarpath jar
                   :pompath pom
                   :build-id build-id})]

    ;; `build-url` and `ana-v` are only set for CircleCI
    (build-log/analysis-kicked-off! build-tracker build-id (:build-url ana-resp) ana-v)

    (try
      (let [build-result (analysis-service/wait-for-build analysis-service ana-resp)
            file-uri     (:analysis-result build-result)
            data         (util/read-cljdoc-edn file-uri)
            ns-count     (let [{:strs [clj cljs]} (:codox data)]
                           (count (set (into (map :name cljs) (map :name clj)))))]
        (build-log/analysis-received! build-tracker build-id file-uri)
        (ingest/ingest-cljdoc-edn storage data)
        (build-log/api-imported! build-tracker build-id ns-count)
        (build-log/completed! build-tracker build-id))
      (catch Exception e
        (build-log/failed! build-tracker build-id "analysis-job-failed")))))

(defn kick-off-build!
  "Run the Git analysis for the provided `project` and kick of an
  analysis build for `project` using the provided `analysis-service`."
  [{:keys [storage build-tracker analysis-service] :as deps}
   {:keys [project version] :as coords}]
  (let [a-uris    (repositories/artifact-uris project version)
        build-id  (build-log/analysis-requested!
                   build-tracker
                   (cljdoc.util/group-id project)
                   (cljdoc.util/artifact-id project)
                   version)
        ana-args  (merge coords a-uris {:build-id build-id})]

    (future
      (try
        ;; Store meta {} and description
        (if-let [scm-info (ingest/scm-info (:pom a-uris))]
          (let [{:keys [error scm-url commit] :as git-result}
                (ingest/ingest-git! storage {:project project
                                             :version version
                                             :scm-url (:url scm-info)
                                             :pom-revision (:sha scm-info)})]
            (when error
              (log/warnf "Error while processing %s %s: %s" project version error))
            (build-log/git-completed! build-tracker build-id (update git-result :error :type))
            (analyze-and-import-api! deps ana-args))
          (analyze-and-import-api! deps ana-args))

        (catch Throwable e
          ;; TODO store in column for internal exception
          (log/error e (format "Exception while processing %s %s (build %s)" project version build-id))
          (build-log/failed! build-tracker build-id "exception-during-import")
          (throw e))))

    build-id))

(comment
  (kick-off-build!
   {:storage (cljdoc.storage.api/->SQLiteStorage (cljdoc.config/db (cljdoc.config/config)))
    :analysis-service (:cljdoc/analysis-service integrant.repl.state/system)
    :build-tracker (:cljdoc/build-tracker integrant.repl.state/system)}
   {:project "bidi" :version "2.1.3"})

  )
