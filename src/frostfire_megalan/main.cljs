(ns frostfire-megalan.main
  (:require [reagent.core :as r]
            [reagent.ratom :as ratom]
            [frostfire-megalan.state :as s]
            ["react-markdown" :as ReactMarkdown]))

(defonce state (r/atom (s/initial-state)))

; helpers
(defn lobby [l]
      (let [{:keys [id game notes players]} l]
           ^{:key id}
           [:div.lobby
            [:div.head
             [:h3 game]
             [:i.fal.fa-times.fa-lg]]
            [:div.mid
             [:span (if (empty? notes) "(No notes entered)" notes)]]
            [:div.body
             (map #(vector :p {:key (:id %)} (:name %)) players)]]))

(defn game [g]
      (let [{:keys [id name notes hi-players players]} g]
           ^{:key id}
           [:div.game
            [:div.head
             [:h3 name]]
            [:div.mid
             [:> ReactMarkdown {:source notes}]]
            [:div.body
             (map #(vector :p {:key (:id %)} (:name %)) hi-players)
             (map #(vector :p {:key (:id %)} (:name %)) players)]]))

; core
(defn header []
      [:div.heading [:h1 "MegaLAN"]])

(defn lobbies []
      [:div.lobbies
       [:div.heading
        [:h2 "Open game lobbies"]]
       [:div.body
        (map lobby (:lobbies @state))]])

(defn games []
      [:div.games
       [:div.heading
        [:h2 "Game list"]]
       [:div.body
        (map game (:games @state))]])

(defn container []
      [:<>
       [header]
       [lobbies]
       [games]])