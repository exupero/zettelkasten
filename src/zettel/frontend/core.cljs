(ns zettel.frontend.core
  (:require [clojure.string :as string]
            [cljs.reader :refer [read-string]]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [ajax.core :refer [GET]]))

(defn fetch-cards [state]
  (GET "/cards.edn"
       {:handler (fn [response]
                   (swap! state assoc :cards (read-string response))
                   (.setItem js/window.localStorage "cards" response)
                   (js/alert "Done!"))
        :error-handler (fn [error]
                         (js/alert error))}))

(defn clear-search [state]
  (swap! state assoc :search []))

(defonce state (r/atom {:search []
                        :cards []}))

(defn matches? [{:keys [id content]} [search-key search-value]]
  (condp = search-key
    :content (re-find (re-pattern (str "(?i)" (string/replace search-value #"\s+" ".*"))) content)
    :id (re-find (re-pattern search-value) id)
    :tag (re-find (re-pattern (str "data-tag=\"" search-value "\"")) content)
    false))

(defn main [state]
  (let [input (r/atom nil)]
    (fn [state]
      (let [{:keys [cards search]} @state]
        [:div
         [:div {:class "flex mb5"}
          [:input {:type :text
                   :ref #(reset! input %)
                   :placeholder "Search"
                   :on-change #(swap! state assoc :search [:content (.. % -target -value)])}]
          [:button {:on-click #(do
                                 (clear-search state)
                                 (set! (.-value @input) ""))}
           "×"]
          [:button {:on-click #(fetch-cards state)} "↻"]]
         (when-not (string/blank? (second search))
           [:div (for [{:keys [id content] :as card} cards
                       :when (matches? card search)]
                   ^{:key id}
                   [:section {:dangerouslySetInnerHTML {:__html content}}])])]))))

(defn get-cards [state]
  (if-let [cards (.getItem js/window.localStorage "cards")]
    (swap! state assoc :cards (read-string cards))
    (fetch-cards state)))

(defn update-search [state]
  (let [[k v] (string/split (subs js/window.location.hash 1) #"=" 2)]
    (swap! state assoc :search [(keyword k) (js/decodeURI v)])
    (try
      (js/window.scrollTo 0 0)
      (catch js/Exception e
        (js/alert e)))))

(defn init []
  (get-cards state)
  (rdom/render [main state] (.getElementById js/document "app"))
  (.addEventListener js/window "hashchange" #(update-search state)))
