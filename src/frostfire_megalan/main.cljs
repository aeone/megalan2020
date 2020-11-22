(ns frostfire-megalan.main
  (:require [reagent.core :as r]
            [reagent.ratom :as ratom]
            [frostfire-megalan.state :as s]))

(defonce state (r/atom (s/initial-state)))

; helpers
(defn lobby [l]
      (let [{:keys [game notes players]} l]
           [:div.lobby
            [:div.head
             [:h3 game]]
            [:div.mid
             [:p notes]]
            [:div.body
             (map #(vector :p %) players)]]))

(defn game [g]
      (let [{:keys [name notes hi-players players]} g]
           [:div.game
            [:div.head
             [:h3 name]]
            [:div.mid
             [:p notes]]
            [:div.body
             (map #(vector :p %) hi-players)
             (map #(vector :p %) players)]]))

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
       (header)
       (lobbies)
       (games)])