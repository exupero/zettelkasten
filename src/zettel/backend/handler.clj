(ns zettel.backend.handler
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [markdown.core :refer [md-to-html-string]]
            [hickory.core :as hickory]
            [hickory.render :refer [hickory-to-html]]))

(defn split-by [pattern s]
  (loop [splits (string/split s pattern)
         matches (re-seq pattern s)
         ret []]
    (let [[split & splits] (seq splits)
          [match & matches] (seq matches)]
      (cond
        (and split match)
        , (recur splits matches (conj ret split match))
        split
        , (conj ret split)
        :else
        ret))))

(defn tag? [node]
  (and (map? node)
       (-> node :content first string?)
       (some->> node :content first (re-find #"\[[^]]+\]:"))))

(defn blockquote? [node]
  (and (map? node)
       (= :blockquote (node :tag))))

(defn link? [node]
  (and (map? node)
       (= :a (node :tag))))

(defn route-link [node]
  (if (->> node :attrs :href (re-find #"/"))
    node
    (update-in node [:attrs :href] (partial str "#id="))))

(defn link-tag [node]
  (let [tags (->> node :content first (re-seq #"\[([^]]+)\]:") (map second))
        links (map #(do {:type :element
                         :tag :a
                         :attrs {:class "tag"
                                 :href (str "#tag=" %)
                                 :data-tag %}
                         :content [%]})
                   tags)]
    (assoc node :content links)))

(defn sup [s]
  (map (fn [s]
         (if (vector? s)
           {:type :element :tag :sup :attrs nil :content [(second s)]}
           s))
       (split-by #"\s?\^([0-9]+)\s*" s)))

(defn md->html [md]
  (-> md
    md-to-html-string
    hickory/parse
    hickory/as-hickory
    (->> (walk/postwalk (fn [n]
                          (cond
                            (link? n)
                            , (route-link n)
                            (tag? n)
                            , (link-tag n)
                            (blockquote? n)
                            , (-> n
                                (->> (walk/postwalk #(if (string? %) (sup %) %)))
                                (->> (walk/postwalk #(if (and (map-entry? %) (= :content (key %)))
                                                       [(key %) (flatten (val %))]
                                                       %))))
                            :else
                            , n))))
    hickory-to-html))

(defn get-cards [path]
  (into []
        (comp
          (remove #(.isDirectory %))
          (filter (comp (partial re-find #"\.md$") #(.getName %)))
          (map (fn [f]
                 {:id (string/replace (.getName f) #"\.md$" "")
                  :content (md->html (slurp f))})))
        (file-seq (io/file path))))

(defn image-paths [path]
  (sequence
    (comp
      (map #(.getName %))
      (filter (partial re-find #"\.(jpg|png|svg)$")))
    (file-seq (io/file path))))

(defn cache-manifest [path]
  (string/join "\n" (concat
                      ["CACHE MANIFEST" "index.html" "js/main.js" "css/style.css"]
                      (image-paths path)
                      ["" "NETWORK:" "cards.edn" "" "CACHE:"])))

(defn handler [req]
  (let [path (System/getenv "ZETTELKASTEN_PATH")]
    (cond
      (= "/cache.manifesto" (req :uri))
      , {:status 200 :body (cache-manifest path)}
      (= "/cards.edn" (req :uri))
      , {:status 200 :body (pr-str (get-cards path))}
      (re-find #"\.png$" (req :uri))
      , (let [f (io/file (str path (req :uri)))]
          (if (.exists f)
            {:status 200 :body f :headers {"Content-type" "image/png"}}
            {:status 404 :body "Image not found"}))
      :else
      , (do
          (prn req)
          {:status 404 :body "Not found"}))))
