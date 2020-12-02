(ns frostfire-megalan.main
  (:require [clojure.core.async :refer [put!]]
            [reagent.core :as r]
            [reagent.ratom :as ratom]
            [frostfire-megalan.state :as state :refer [state-update-chan]]
            ["react-markdown" :as ReactMarkdown]))

; helpers
(defn lobby [l all-players]
      (let [{:keys [id game notes players]} l
            players (filter #((set (keys players)) (:id %)) all-players)
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

(defn game [g all-players]
      (let [{:keys [id name notes hi-players players]} g
            hi-players (filter #((set (keys hi-players)) (:id %)) all-players)
            players (filter #((set (keys players)) (:id %)) all-players)
            listener #(when-let
                        [_ (js/confirm
                             (str "Do you want to create a lobby for " name "?"))]
                        (js/console.log (str "Creating lobby for " name))
                        (let [uuid (str (random-uuid))]
                             (put! state-update-chan
                                   [[:lobbies uuid]
                                    (state/lobby uuid name "" {})])))]
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
      [:div.main-heading [:h1.mega "Mega"] [:h1.lan "LAN"]])

(defn my-status []
      [:div.my-status
       [:div.name
        [:span "you are Jane Doe "]
        [:span.link "(change user)"]]
       [:div.statuses
        [:div.free "free" [:br] [:span.desc "I'm available for games"]]
        [:div.soon "soon" [:br] [:span.desc "I'll be available soon"]]
        [:div.busy "busy" [:br] [:span.desc "Currently playing something"]]
        [:div.away "away" [:br] [:span.desc "Not doing MegaLAN"]]]
       [:div.status-age
        [:span "status set x minutes ago "]
        [:span.link "(refresh now)"]]])

(defn lobbies [ls all-players]
      [:div.lobbies
       [:div.heading
        [:h2 "Open game lobbies"]]
       [:div.body
        (map #(lobby % all-players) ls)]])

(defn games [gs all-players]
      [:div.games
       [:div.heading
        [:h2 "Game list"]]
       [:div.body
        (map #(game % all-players) gs)]])

(defn container [state internal-state]
      (let [all-players (vals (:players @state))
            ls (vals (:lobbies @state))
            gs (vals (:games @state))]
           [:<>
            [header]
            [my-status]
            ;[:span.test "test"]
            [lobbies ls all-players]
            [games gs all-players]]))

; modals
(defn create-game []
      [:div.modal-bg
       [:div.modal
        [:h2 "Create a game"]]])