(require '[babashka.pods :as pods])

(pods/load-pod "bootleg")
(pods/load-pod 'org.babashka/filewatcher "0.0.1")

(ns server
  (:require [clojure.java.browse :as browse]
            [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [hiccup2.core :as html]
            [org.httpkit.server :as server]
            [pod.babashka.filewatcher :as fw]
            [pod.retrogradeorbit.bootleg.markdown :refer [markdown]]
            [pod.retrogradeorbit.bootleg.utils :as utils]))

(defn cache-manifest [dir]
  (string/join "\n" (concat
                      ["CACHE MANIFEST"
                       "# Version 1.1"
                       "index.html"
                       "style.css"
                       "app.js"]
                      (sequence
                        (comp
                          (filter (comp (partial re-find #"\.(jpg|png|svg)$") fs/file-name))
                          (map #(.relativize dir %)))
                        (fs/list-dir dir))
                      ["" "NETWORK:" "cards.json"
                       "" "CACHE:"
                       "" "FALLBACK:"])))

(defn reformat-node [id node]
  (cond
    (or (not (map? node)) (nil? (node :tag)))
    , node
    (= :h1 (node :tag))
    , [(node :tag) (node :content) id]
    (= :a (node :tag))
    , [(node :tag) (node :content) (-> node :attrs :href)]
    (= :blockquote (node :tag))
    , [(node :tag) (mapcat (fn [c]
                             (if (and (string? c) (re-find #"\s*\^\d+" c))
                               (let [[_ a b] (re-find #"\^(\d+)\s(.*)" c)]
                                 [[:sup a] b])
                               [c]))
                           (node :content))]
    (and (= :p (node :tag))
         (some (every-pred string? (partial re-find #"\[[^\]]+\]:"))
               (node :content)))
    , [:tags (map second (re-seq #"\[([^\]]+)\]:" (string/join (node :content))))]
    :else
    , [(node :tag) (node :content)]))

(def card-cache (atom {}))

(defn render-card! [dir f]
  (when (string/ends-with? f ".md")
    (let [[_ id] (re-find #"([^/]+)\.md$" (str f))]
      (swap! card-cache assoc id
             (-> (slurp (fs/file f))
               (markdown :data :hickory-seq)
               (->> (clojure.walk/postwalk #(reformat-node id %))))))))

(defn watch-cards [dir]
  (fw/watch dir (fn [{:keys [type path] :as event}]
                  (when (= type :create)
                    (try
                      (render-card! dir path)
                      (catch Exception e
                        (prn e)))))))

(defn response [body content-type]
  {:status 200
   :body body
   :headers {"Content-Type" content-type}})

(defn handler [dir {:keys [uri]}]
  (cond
    (#{"/" "/index.html"} uri)
    , (response (fs/file "index.html") "text/html")
    (= uri "/cache.manifest")
    , (response (cache-manifest dir) "text/cache-manifest")
    (= uri "/style.css")
    , (response (fs/file "style.css") "text/css")
    (= uri "/app.js")
    , (response (fs/file "app.js") "application/javascript")
    (= uri "/cards.json")
    , (response (json/generate-string @card-cache) "application/json")
    (re-find #"\.png$" uri)
    , (let [f (fs/file (str dir uri))]
        (if (.exists f)
          (response f "image/png")
          {:status 404 :body "Image not found"}))
    :else
    , {:status 404 :body (str "Not found: " uri)}))

(defonce server (atom nil))

(defn serve [{:keys [port dir join?]}]
  (assert (fs/directory? dir) (str "The given path `" dir "` is not a directory."))
  (println "Caching cards")
  (doseq [f (fs/list-dir dir)]
    (render-card! dir f))
  (watch-cards dir)
  (println "Starting HTTP server at" port "for" (str dir))
  (let [dir (fs/path dir)]
    (reset! server (server/run-server #(handler dir %) {:port port})))
  (println "Started server")
  (browse/browse-url (format "http://localhost:%s/" port))
  (when join? @(promise)))

(def cli-options
  [["-d" "--directory PATH" "directory from which to read note files"]
   ["-p" "--port PORT" "port to serve on"
    :parse-fn #(Integer/parseInt %)]])

(defn start-server []
  (let [{{:keys [port directory] :or {port 8090 directory "."}} :options}
        , (parse-opts *command-line-args* cli-options)]
    (serve {:port port :dir directory :join? true})))
