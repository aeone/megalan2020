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
               (map #(do ^{:key (:id %)}
                          [:div.player {:class [(:status %)]}
                             [:img.avatar {:src (str "https://www.gravatar.com/avatar/" (.md5 js/window (:gravatar-email %)))}]
                             [:span.name {:key (:id %)} (:name %)]]) players))
             [:button.point {:on-click add-self-listener} "Add self to lobby"]]]))

(def cname name)

(defn game [g all-players my-uuid]
      (let [{:keys [id name notes hi-players players]} g
            hi-players (filter #((set (map cname (keys hi-players))) (:id %)) all-players)
            players (filter #((set (map cname (keys players))) (:id %)) all-players)
            listener #(when-let
                        [_ (js/confirm
                             (str "Do you want to create a lobby for " name "?"))]
                        (js/console.log (str "Creating lobby for " name))
                        (let [uuid (str (random-uuid))]
                             (put! state-update-chan
                                   [[:lobbies uuid]
                                    (state/lobby uuid name "" {})])))
            ;im-high-priority (contains? (map cname (keys hi-players)) my-uuid)
            ;im-potential-plyr (contains? (map cname (keys players)) my-uuid)
            im-high-priority (seq (filter #(= (:id %) my-uuid) hi-players))
            im-potential-plyr (seq (filter #(= (:id %) my-uuid) players))
            add-high-listener #(do
                                 (put! state-update-chan [[:games id :hi-players my-uuid] true])
                                 (put! state-update-chan [[:games id :players my-uuid]]))
            add-plyr-listener #(do
                                 (put! state-update-chan [[:games id :players my-uuid] true])
                                 (put! state-update-chan [[:games id :hi-players my-uuid]]))
            rm-self-listener #(do
                                 (put! state-update-chan [[:games id :hi-players my-uuid]])
                                 (put! state-update-chan [[:games id :players my-uuid]]))]
           ;(js/console.log my-uuid)
           (js/console.log im-high-priority)
           (js/console.log hi-players)
           (js/console.log (count (filter #(= (:id %) my-uuid) hi-players)))
           ;(js/console.log (map cname (keys hi-players)))
           ;(js/console.log (map cname (keys players)))
           ^{:key id}
           [:div.game
            [:div.head
             [:h3 name]]
            [:div.mid
             [:> ReactMarkdown {:source notes}]]
            [:div.body
             [:p.dim "high priority players"]
             (if (empty? hi-players)
               [:p "(no high priority players)"]
               (map #(vector :p {:key (:id %)} (:name %)) hi-players))
             [:p.dim "potential players"]
             (if (empty? players)
               [:p "(no potential players)"]
               (map #(vector :p {:key (:id %)} (:name %)) players))
             (cond
               im-high-priority [:<>
                                 [:button {:on-click add-plyr-listener} "Switch yourself to normal priority"]
                                 [:button {:on-click rm-self-listener} "Remove yourself from player list"]]
               im-potential-plyr [:<>
                                  [:button {:on-click add-high-listener} "Switch yourself to high priority"]
                                  [:button {:on-click rm-self-listener} "Remove yourself from player list"]]
               :else [:<>
                      [:button {:on-click add-high-listener} "Add yourself as high priority player"]
                      [:button {:on-click add-plyr-listener} "Add yourself as potential player"]])]
            [:div.foot
             [:button.create-lobby.point
              {:on-click listener}
              (str "Create lobby for " name)]]]))

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
            status-age-mins (js/Math.floor (/ (- now (:status-set me)) (* 1000 60)))
            refresh #(put! state-update-chan [[:players id :status-set] (.now js/Date)])]
           [:div.my-status
            [:div.name
             [:span (str "you are " (:name me) " ")]
             [:span.link {:on-click #(swap! state/internal-state (fn [s] (dissoc s "player-uuid")))}
              "(change user)"]]
            [:div.statuses
             [:div.free {:class    [(when free "active")]
                         :on-click #(do (refresh) (put! state-update-chan [[:players id "status"] "free"]))}
              [:span.name "free"] [:br] [:span.desc "I'm available for games"]]
             [:div.soon {:class [(when soon "active")]
                         :on-click #(do (refresh) (put! state-update-chan [[:players id "status"] "soon"]))}
              [:span.name "soon"] [:br] [:span.desc "I'll be available soon"]]
             [:div.busy {:class [(when busy "active")]
                         :on-click #(do (refresh) (put! state-update-chan [[:players id "status"] "busy"]))}
              [:span.name "busy"] [:br] [:span.desc "Currently playing something"]]
             [:div.away {:class [(when away "active")]
                         :on-click #(do (refresh) (put! state-update-chan [[:players id "status"] "away"]))}
              [:span.name "away"] [:br] [:span.desc "Not doing MegaLAN"]]]
            [:div.status-age
             [:span (str "status set " status-age-mins " minute" (when (not= 1 status-age-mins) "s") " ago ")]
             [:span.link {:on-click refresh}
              "(refresh now)"]]]))

(defn lobbies [ls all-players my-uuid]
      [:div.lobbies
       [:div.heading
        [:h2 "Open game lobbies"]]
       [:div.body
        (map #(lobby % all-players my-uuid) ls)]])

(defn games [gs all-players my-uuid]
      [:div.games
       [:div.heading
        [:h2 "Game list"]
        [:span.link "see only games I'm interested in"]
        [:span.link {:on-click #(swap! state/internal-state (fn [s] (assoc s "modal" "create-game")))} "create a new game"]]
       [:div.body
        (map #(game % all-players my-uuid) gs)]])

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
      (let [a-name (r/atom "")
            a-sponsor (r/atom "")
            a-notes (r/atom "")]
         (fn []
              [:div.modal
               [:h1 "Create a game"]
               [:h2 "Info"]
               [:p "For the game notes, specify information like:"]
               [:ul
                [:li "Elevator pitch - what kind of game is it, and what kind of person would like it?"]
                [:li "Number of players supported"]
                [:li "Number of players ideal"]
                [:li "Equipment / licenses required (e.g. 'buy on Steam' / 'needs controller' / 'phone only')"]
                [:li "Any places where licenses can be obtained (e.g. 'copies exist on Softwire Steam accounts')"]]

               [:h2 "Details"]
               [:input {:placeholder "Name of game"
                        :value @a-name
                        :on-change #(reset! a-name (.. % -target -value))}]
               [:input {:placeholder "If a PC game, name of sponsor certifying the game is Not A Virus"
                        :value @a-sponsor
                        :on-change #(reset! a-sponsor (.. % -target -value))}]
               [:textarea {:placeholder "Game notes (markdown supported)"
                        :value @a-notes
                        :on-change #(reset! a-notes (.. % -target -value))}]

               [:button {:on-click #(let [game (state/create-game @a-name @a-sponsor @a-notes)]
                                         (put! state-update-chan [[:games (:id game)] game])
                                         (swap! state/internal-state (fn [s] (dissoc s "modal"))))}
                "Create this game"]
               [:button {:on-click #(swap! state/internal-state (fn [s] (dissoc s "modal")))}
                "Nope, cancel, completely abandon this"]])))

; container
(defn container [state internal-state]
      (let [pending-load (:pending-load @state)
            all-players (vals (:players @state))
            ls (vals (:lobbies @state))
            gs (vals (:games @state))
            on-login-screen (not (contains? @internal-state "player-uuid"))
            on-create-game-screen (get @internal-state "modal")
            current-player (get @internal-state "player-uuid")]
           (cond
             pending-load [:div.modal
                           [:h1 "Reticulating splines"]
                           [:h2 "(loading data from firebase, please wait)"]]
             on-login-screen [login all-players]
             on-create-game-screen [create-game]
             :else [:<>
                      [header]
                      [my-status current-player all-players]
                      [lobbies ls all-players current-player]
                      [games gs all-players current-player]])))
