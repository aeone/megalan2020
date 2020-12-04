(ns frostfire-megalan.main
  (:require [clojure.core.async :refer [put! go-loop timeout <!]]
            [reagent.core :as r]
            [reagent.ratom :as ratom]
            [frostfire-megalan.state :as state :refer [state-update-chan state-mod-chan]]
            ["react-markdown" :as ReactMarkdown]))

; helpers
(defn lobby-notes [id notes]
      (let [a-notes-active (r/atom false)
            a-notes (r/atom "")]
           (fn [id notes]
               [:div.mid
                (if @a-notes-active
                  [:<>
                   [:textarea {:placeholder "Write lobby notes - e.g. 'we're in discord voice channel megalan-amethyst'"
                               :rows 10
                               :value       @a-notes
                               :on-change   #(reset! a-notes (.. % -target -value))}]
                   [:button {:on-click #(do (put! state-update-chan [[:lobbies (keyword id) :notes] @a-notes])
                                            (reset! a-notes-active false))}
                    "Save"]]
                  [:<>
                   [:span (if (empty? notes) "(No notes entered)" [:> ReactMarkdown {:source notes}])]
                   [:i.fal.fa-edit.fa-lg.edit-icon.point.link {:on-click
                                                               #(do (reset! a-notes notes)
                                                                    (reset! a-notes-active true))}]])]
               )))

(defn lobby [l all-players my-uuid]
      (let [{:keys [id game notes players]} l
            players (filter #((set (map name (keys players))) (:id %)) all-players)
            p-msg (if (empty? players) "no players" (clojure.string.join ", " (map :name players)))
            confirm-msg (str "Do you want to delete the lobby for " game " containing " p-msg "?")
            kill-listener #(when-let [_ (js/confirm confirm-msg)]
                                (put! state-update-chan
                                      [[:lobbies id] nil]))
            add-self-listener #(let [update-func (state/add-self-to-lobby-update-gen my-uuid id)]
                                    (put! state-mod-chan [update-func]))
            rm-self-listener #(put! state-update-chan [[:lobbies id :players (keyword my-uuid)]])
            im-in-lobby ((set (map :id players)) my-uuid)
            a-notes-active (r/atom false)
            a-notes (r/atom notes)]

               ^{:key id}
               [:div.lobby
                [:div.head
                 [:h3 "Lobby: " game]
                 [:i.fal.fa-times.fa-2x.close-icon.point.link
                  {:on-click kill-listener}]]
                [lobby-notes id notes]
                [:div.body
                 (if (empty? players)
                   [:p "(No players in lobby)"]
                   [:div.players
                    (map #(do ^{:key (:id %)}
                              [:div.player {:class         [(:status %)]
                                            :on-mouse-over (fn [] (swap! state/internal-state (fn [s] (assoc s "player-tooltip" %))))
                                            :on-mouse-out  (fn [] (swap! state/internal-state (fn [s] (dissoc s "player-tooltip"))))
                                            }
                               [:img.avatar {:src (str "https://www.gravatar.com/avatar/" (.md5 js/window (:gravatar-email %)))}]
                               [:span.name {:key (:id %)} (:name %)]]) players)])
                 (if im-in-lobby
                   [:button {:on-click rm-self-listener} "Remove self from lobby"]
                   [:button {:on-click add-self-listener} "Add self to lobby"])]]))

(def cname name)

(defn game [g all-players my-uuid]
      (let [{:keys [id name notes hi-players players]} g
            hi-players (filter #((set (map cname (keys hi-players))) (:id %)) all-players)
            players (filter #((set (map cname (keys players))) (:id %)) all-players)
            listener #(when-let
                        [_ (js/confirm
                             (str "Do you want to create a lobby for " name "?"))]
                        (js/console.log (str "Creating lobby for " name))
                        (let [l (state/create-lobby name)]
                             (put! state-update-chan [[:lobbies (keyword (:id l))] l])))
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
                                 (put! state-update-chan [[:games id :players my-uuid]]))
            player #(do [:img.avatar {:src (str "https://www.gravatar.com/avatar/" (.md5 js/window (:gravatar-email %)))}])]
           ^{:key id}
           [:div.game
            [:div.head
             [:h3 name]
             [:i.fal.fa-edit.fa-lg.edit-icon.point.link {:on-click
                                                         #(swap! state/internal-state (fn [s] (assoc s "edit-game" id)))}]]
            [:div.mid
             [:> ReactMarkdown {:source notes}]]
            [:div.body
             [:h4 (str "high priority players" (when-not (empty? hi-players) (str " (" (count hi-players) ")")))]
             (if (empty? hi-players)
               [:p.dim "(no high priority players)"]
               [:div.players
                (map #(vector :p.player {:key           (:id %)
                                         :class         [(:status %)]
                                         :on-mouse-over (fn [] (swap! state/internal-state (fn [s] (assoc s "player-tooltip" %))))
                                         :on-mouse-out  (fn [] (swap! state/internal-state (fn [s] (dissoc s "player-tooltip"))))
                                         } (player %)) hi-players)])
             [:h4 (str "potential players" (when-not (empty? players) (str " (" (count players) ")")))]
             (if (empty? players)
               [:p.dim "(no potential players)"]
               [:div.players
                (map #(vector :p.player {:key           (:id %)
                                         :class         [(:status %)]
                                         :on-mouse-over (fn [] (swap! state/internal-state (fn [s] (assoc s "player-tooltip" %))))
                                         :on-mouse-out  (fn [] (swap! state/internal-state (fn [s] (dissoc s "player-tooltip"))))
                                         } (player %)) players)])
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

(def status-poke (r/atom (.now js/Date)))
(go-loop []
         (<! (timeout (* 30 1000)))
         (reset! status-poke (.now js/Date))
         (recur))

(defn my-status [current-player all-players]
      (let [me (first (filter #(= (:id %) current-player) all-players))
            id (:id me)
            free (= (:status me) "free")
            soon (= (:status me) "soon")
            busy (= (:status me) "busy")
            away (= (:status me) "away")
            now @status-poke
            status-age-mins (js/Math.floor (/ (- now (:status-set me)) (* 1000 60)))
            refresh #(put! state-update-chan [[:players id :status-set] (.now js/Date)])]
           [:div.my-status
            [:div.name
             [:span.dim (str "you are ")]
             [:span (str (:name me) " ")]
             [:span.link.dim {:on-click #(swap! state/internal-state (fn [s] (assoc s "edit-user" me)))} "(edit info)"]
             [:span " "]
             [:span.link.dim {:on-click #(swap! state/internal-state (fn [s] (dissoc s "player-uuid")))}
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
            [:div.status-age.dim
             [:span (str "status set " status-age-mins " minute" (when (not= 1 status-age-mins) "s") " ago ")]
             [:span.link {:on-click refresh}
              "(refresh now)"]]]))

(defn lobbies [ls all-players my-uuid]
      [:div.lobbies
       [:div.heading
        [:h2 "Open game lobbies"]]
       [:div.body
        (map #(lobby % all-players my-uuid) ls)]])

(defn games [gs all-players my-uuid filtering]
      (let [gs (cond filtering (filter #(state/in? (map name (concat (keys (:hi-players %)) (keys (:players %)))) my-uuid) gs)
                     :else gs)]
           [:div.games
            [:div.heading
             [:h2 "Game list"]
             (if filtering
               [:span.link {:on-click #(swap! state/internal-state (fn [s] (assoc s "filter-games" false)))}
                "see all games in the game list"]
               [:span.link {:on-click #(swap! state/internal-state (fn [s] (assoc s "filter-games" true)))}
                "see only games I'm interested in"])
             [:span.link {:on-click #(swap! state/internal-state (fn [s] (assoc s "modal" "create-game")))} "create a new game"]]
            [:div.body
             (map #(game % all-players my-uuid) gs)]]))

; modals
(defn login [all-players]
      (let [a-name (r/atom "")
            a-email (r/atom "")
            a-notes (r/atom "")
            a-sel-id (r/atom "")]
           (fn [all-players]
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
                  [:textarea {:placeholder "Notes for you, e.g. your discord username, battle.net, steam, switch friend codes, etc. (supports markdown)"
                              :rows 10
                              :value       @a-notes
                              :on-change   #(reset! a-notes (.. % -target -value))}]
                  [:button {:on-click #(let [player (state/create-player @a-name @a-email @a-notes)]
                                            ;(.preventDefault %)
                                            (swap! state/internal-state (fn [s]
                                              (assoc-in s ["player-uuid"] (:id player))))
                                            (put! state-update-chan [[:players (:id player)] player]))}
                   "Create & log in as user"]]]
                ])))

(defn edit-user [user]
      (let [a-name (r/atom (:name user))
            a-email (r/atom (:gravatar-email user))
            a-notes (r/atom (:notes user))]
           (fn []
               [:div.modal
                [:h2 "Edit user"]
                [:div.row
                 [:img.demo-av {:src (str "https://www.gravatar.com/avatar/" (when (not (empty? @a-email)) (.md5 js/window @a-email)))}]
                 [:div.col
                  [:input {:placeholder "Your name, as it will be displayed to all users"
                           :value       @a-name
                           :on-change   #(reset! a-name (.. % -target -value))}]
                  [:input {:placeholder "Your email, for Gravatar to supply you with an avatar"
                           :value       @a-email
                           :on-change   #(reset! a-email (.. % -target -value))}]
                  [:textarea {:placeholder "Notes for you, e.g. your discord username, battle.net, steam, switch friend codes, etc. (supports markdown)"
                              :rows        10
                              :value       @a-notes
                              :on-change   #(reset! a-notes (.. % -target -value))}]
                  [:button {:on-click #(do (put! state-update-chan [[:players (keyword (:id user)) :name] @a-name])
                                           (put! state-update-chan [[:players (keyword (:id user)) :gravatar-email] @a-email])
                                           (put! state-update-chan [[:players (keyword (:id user)) :notes] @a-notes])
                                           (swap! state/internal-state (fn [s] (dissoc s "edit-user"))))}
                   "Save changes to user"]]]])))

(defn create-game [game]
      (let [a-name (r/atom (:name game))
            a-sponsor (r/atom (:sponsor game))
            a-notes (r/atom (:notes game))]
         (fn [game]
              [:div.modal
               [:h1 (if game (str "Edit game: " (:name game)) "Create a game")]
               [:h2 "Info"]
               [:p "For the game notes, specify information like:"]
               [:ul
                [:li "Elevator pitch - what kind of game is it, and what kind of person would like it?"]
                [:li "Number of players supported"]
                [:li "Number of players ideal"]
                [:li "How long a game tends to take"]
                [:li "How beginner-friendly it is"]
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
                           :rows 10
                        :value @a-notes
                        :on-change #(reset! a-notes (.. % -target -value))}]

               (if game
                 [:button {:on-click #(do (put! state-update-chan [[:games (keyword (:id game)) :name] @a-name])
                                          (put! state-update-chan [[:games (keyword (:id game)) :sponsor] @a-sponsor])
                                          (put! state-update-chan [[:games (keyword (:id game)) :notes] @a-notes])
                                          (swap! state/internal-state (fn [s] (dissoc s "edit-game"))))}
                  "Save changes to this game"]
                 [:button {:on-click #(let [game (state/create-game @a-name @a-sponsor @a-notes)]
                                           (put! state-update-chan [[:games (:id game)] game])
                                           (swap! state/internal-state (fn [s] (dissoc s "modal"))))}
                  "Create this game"])
               [:button {:on-click #(swap! state/internal-state (fn [s] (dissoc s "modal")))}
                "Nope, cancel this (discard changes)"]])))

(defn tooltip [p]
      (let [
            {:keys [name gravatar-email notes status status-set]} p
            now (.now js/Date)
            status-age-mins (js/Math.floor (/ (- now status-set) (* 1000 60)))]
           [:div.tooltip
            (when gravatar-email
              [:img.avatar.big {:src (str "https://www.gravatar.com/avatar/" (.md5 js/window gravatar-email))}])
            [:div.contents
             [:h3 name]
             [:p.dim (str "status: " status " for " status-age-mins " min.")]
             [:div.notes [:> ReactMarkdown {:source notes}]]]
            ]
           ))

; container
(defn container [state internal-state]
      (let [pending-load (:pending-load @state)
            all-players (vals (:players @state))
            ls (vals (:lobbies @state))
            gs (vals (:games @state))
            on-login-screen (not (contains? @internal-state "player-uuid"))
            on-create-game-screen (get @internal-state "modal")
            editing-user (get @internal-state "edit-user")
            editing-game (get @internal-state "edit-game")
            game-being-edited (first (filter #(= (:id %) editing-game) gs))
            current-player (get @internal-state "player-uuid")
            player-tooltip (get @internal-state "player-tooltip")
            filtering-games (get @internal-state "filter-games")]
           (cond
             pending-load [:div.modal
                           [:h1 "Reticulating splines"]
                           [:h2 "(loading data from firebase, please wait)"]]
             on-login-screen [login all-players]
             editing-user [edit-user editing-user]
             on-create-game-screen [create-game false]
             editing-game [create-game game-being-edited]
             :else [:<>
                      [header]
                      [my-status current-player all-players]
                      [lobbies ls all-players current-player]
                      [games gs all-players current-player filtering-games]
                      (when player-tooltip
                          [tooltip player-tooltip])])))
