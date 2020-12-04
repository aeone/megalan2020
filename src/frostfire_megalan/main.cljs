(ns frostfire-megalan.main
  (:require [clojure.core.async :refer [put!]]
            [reagent.core :as r]
            [reagent.ratom :as ratom]
            [frostfire-megalan.state :as state :refer [state-update-chan state-mod-chan]]
            ["react-markdown" :as ReactMarkdown]))

; helpers
(defn lobby [l all-players my-uuid]
      (let [{:keys [id game notes players]} l
            players (filter #((set (map name (keys players))) (:id %)) all-players)
            p-msg (if (empty? players) "no players" (clojure.string.join ", " (map :name players)))
            confirm-msg (str "Do you want to delete the lobby for " game " containing " p-msg "?")
            kill-listener #(when-let [_ (js/confirm confirm-msg)]
                                (put! state-update-chan
                                      [[:lobbies id] nil]))
            add-self-listener #(let [update-func (state/add-self-to-lobby-update-gen my-uuid id)]
                                    (put! state-mod-chan [update-func]))]
           ^{:key id}
           [:div.lobby
            [:div.head
             [:h3 "Lobby: " game]
             [:i.fal.fa-times.fa-lg.close-icon.point.link
              {:on-click kill-listener}]]
            [:div.mid
             [:span (if (empty? notes) "(No notes entered)" notes)]
             [:i.fal.fa-edit.fa-lg.edit-icon.point.link]]
            [:div.body
             (if (empty? players)
               "(No players in lobby)"
               (map #(vector :div.player {:class [(:status %)]}
                             [:img.avatar {:src (str "https://www.gravatar.com/avatar/" (.md5 js/window (:gravatar-email %)))}]
                             [:span.name {:key (:id %)} (:name %)]) players))
             [:button.point {:on-click add-self-listener} "Add self to lobby"]]]))

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

(defn my-status [current-player all-players]
      (let [me (first (filter #(= (:id %) current-player) all-players))
            id (:id me)
            free (= (:status me) "free")
            soon (= (:status me) "soon")
            busy (= (:status me) "busy")
            away (= (:status me) "away")
            now (.now js/Date)
            status-age-mins (js/Math.floor (/ (- now (:status-set me)) (* 1000 60)))]
           [:div.my-status
            [:div.name
             [:span (str "you are " (:name me) " ")]
             [:span.link {:on-click #(swap! state/internal-state (fn [s] (dissoc s "player-uuid")))}
              "(change user)"]]
            [:div.statuses
             [:div.free {:class [(when free "active")]
                         :on-click #(put! state-update-chan [[:players id "status"] "free"])}
              [:span.name "free"] [:br] [:span.desc "I'm available for games"]]
             [:div.soon {:class [(when soon "active")]
                         :on-click #(put! state-update-chan [[:players id "status"] "soon"])}
              [:span.name "soon"] [:br] [:span.desc "I'll be available soon"]]
             [:div.busy {:class [(when busy "active")]
                         :on-click #(put! state-update-chan [[:players id "status"] "busy"])}
              [:span.name "busy"] [:br] [:span.desc "Currently playing something"]]
             [:div.away {:class [(when away "active")]
                         :on-click #(put! state-update-chan [[:players id "status"] "away"])}
              [:span.name "away"] [:br] [:span.desc "Not doing MegaLAN"]]]
            [:div.status-age
             [:span (str "status set " status-age-mins " minute" (when (not= 1 status-age-mins) "s") " ago ")]
             [:span.link {:on-click #(put! state-update-chan [[:players id :status-set] (.now js/Date)])}
              "(refresh now)"]]]))

(defn lobbies [ls all-players my-uuid]
      [:div.lobbies
       [:div.heading
        [:h2 "Open game lobbies"]]
       [:div.body
        (map #(lobby % all-players my-uuid) ls)]])

(defn games [gs all-players]
      [:div.games
       [:div.heading
        [:h2 "Game list"]]
       [:div.body
        (map #(game % all-players) gs)]])

; modals
(defn login [all-players]
      (let [a-name (r/atom "")
            a-email (r/atom "")
            a-notes (r/atom "")
            a-sel-id (r/atom "")]
           (fn []
               [:div.modal
                [:h1 "Log in or create user"]
                [:p "The MegaLAN site operates with the 'Google spreadsheet' permission model: everyone is allowed to do everything." [:br] "Please take care."]
                [:p "Select your name from the drop-down list below, or create a new user."]
                [:h2 "Select existing user"]
                [:select#user-uuid {:on-change (fn [e] (js/console.log e) (reset! a-sel-id (clojure.string.split (.. e -target -value) #",")))}
                 [:option "Choose a user"]
                 (map #(do ^{:key (:id %)}
                           [:option {:value (str (:id %) "," (:name %))} (:name %)]) all-players)]
                [:button {:on-click #(when (not (empty? @a-sel-id)) (swap! state/internal-state (fn [s] (assoc-in s ["player-uuid"] (first @a-sel-id)))))}
                 (if (empty? @a-sel-id) "(Choose a user)" (str "Log in as " (second @a-sel-id)))]
                [:h2 "Create user"]
                [:div.row
                 [:img.demo-av {:src (str "https://www.gravatar.com/avatar/" (when (not (empty? @a-email)) (.md5 js/window @a-email)))}]
                 [:div.col
                  [:input {:placeholder "Your name, as it will be displayed to all users"
                           :value       @a-name
                           :on-change   #(reset! a-name (.. % -target -value))}]
                  [:input {:placeholder "Your email, for Gravatar to supply you with an avatar"
                           :value       @a-email
                           :on-change   #(reset! a-email (.. % -target -value))}]
                  [:textarea {:placeholder "Notes for you, e.g. your discord username, battle.net, steam, switch friend codes, etc."
                              :value       @a-notes
                              :on-change   #(reset! a-notes (.. % -target -value))}]
                  [:button {:on-click #(let [player (state/create-player @a-name @a-email @a-notes)]
                                            ;(.preventDefault %)
                                            (swap! state/internal-state (fn [s]
                                              (assoc-in s ["player-uuid"] (:id player))))
                                            (put! state-update-chan [[:players (:id player)] player]))}
                   "Create & log in as user"]]]
                ])))

(defn create-game []
      [:div.modal-bg
       [:div.modal
        [:h2 "Create a game"]]])

; container
(defn container [state internal-state]
      (let [pending-load (:pending-load @state)
            all-players (vals (:players @state))
            ls (vals (:lobbies @state))
            gs (vals (:games @state))
            on-login-screen (not (contains? @internal-state "player-uuid"))
            current-player (get @internal-state "player-uuid")]
           (cond
             pending-load [:div.modal [:h1 "Loading from firebase."]]
             on-login-screen [login all-players]
             :else [:<>
                      [header]
                      [my-status current-player all-players]
                      [lobbies ls all-players current-player]
                      [games gs all-players]])))
