(ns frostfire-megalan.main
  (:require [clojure.core.async :refer [put!]]
            [reagent.core :as r]
            [reagent.ratom :as ratom]
            [frostfire-megalan.state
              :as state
              :refer [state internal-state state-update-chan]]
            ["react-markdown" :as ReactMarkdown]))

; helpers
(defn lobby [l]
      (let [{:keys [id game notes players]} l
            p-msg (if (empty? players) "no players" (clojure.string.join ", " (map :name players)))
            confirm-msg (str "Do you want to delete the lobby for " game " containing " p-msg "?")
            listener #(when-let [_ (js/confirm confirm-msg)]
                                (put! state-update-chan
                                      [[:lobbies id] nil]))]
           ^{:key id}
           [:div.lobby
            [:div.head
             [:h3 "Lobby: " game]
             [:i.fal.fa-times.fa-lg.close-icon.point
              {:on-click listener}]]
            [:div.mid
             [:span (if (empty? notes) "(No notes entered)" notes)]]
            [:div.body
             (if (empty? players)
               "(No players in lobby)"
               (map #(vector :p {:key (:id %)} (:name %)) players))]]))

(defn game [g]
      (let [{:keys [id name notes hi-players players]} g
            listener #(when-let
                        [_ (js/confirm
                             (str "Do you want to create a lobby for " name "?"))]
                        (js/console.log (str "Creating lobby for " name))
                        (let [uuid (str (random-uuid))]
                             (put! state-update-chan
                                   [[:lobbies uuid]
                                    (state/lobby uuid name "" [])])))]
           ^{:key id}
           [:div.game
            [:div.head
             [:h3 name]]
            [:div.mid
             [:> ReactMarkdown {:source notes}]]
            [:div.body
             (map #(vector :p {:key (:id %)} (:name %)) hi-players)
             (map #(vector :p {:key (:id %)} (:name %)) players)]
            [:div.foot
             [:button.create-lobby.point
              {:on-click listener}
              "Create lobby"]]]))

; core
(defn header []
      [:div.heading [:h1 "MegaLAN"]])

(defn lobbies []
      [:div.lobbies
       [:div.heading
        [:h2 "Open game lobbies"]]
       [:div.body
        (map lobby (vals (:lobbies @state)))]])

(defn games []
      [:div.games
       [:div.heading
        [:h2 "Game list"]]
       [:div.body
        (map game (vals (:games @state)))]])

(defn container []
      [:<>
       [header]
       [lobbies]
       [games]])

; modals
(defn create-game []
      [:div.modal-bg
       [:div.modal
        [:h2 "Create a game"]]])