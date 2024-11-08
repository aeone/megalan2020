(ns megalan2021.views
  (:require
   [re-frame.core :as re-frame]
   [re-com.core :as re-com :refer [at]]
   [reagent.core :as r]
   [megalan2021.config :as config]
   [megalan2021.events :as events]
   [megalan2021.evt :as evt]
   [megalan2021.subs :as subs]
   [clojure.string :as string]
   [clojure.core :as core]
   [clojure.core.async :refer [put! go-loop timeout <!]]
   ["react-markdown" :as ReactMarkdown]))

(declare main-header
         loading-modal
         log-in-modal
         edit-user-modal
         edit-game-modal
         my-status
         lobbies
         lobby
         lobby-notes
         games
         game
         archived-games
         archived-game
         player
         tooltip
         footer-version)

;; (defn title []
;;   (let [name (re-frame/subscribe [::subs/name])
;;         games @(re-frame/subscribe [::subs/games])]
;;     [re-com/title
;;      :src   (at)
;;      :label (str "Hello from " @name". Git version: " config/version ".")
;;      :level :level1]))

(defn footer-version [] 
  [:div "Git version: " config/version])


(def status-poke (r/atom (.now js/Date)))
(go-loop []
  (<! (timeout (* 30 1000)))
  (reset! status-poke (.now js/Date))
  (recur))

(defn p-sort-priority [p]
  (case (:status p)
    "free" 1
    "soon" 2
    "busy" 3
    "away" 4))

(defonce tooltip-pinned (r/atom false))
(defonce tooltip-hover (r/atom false))
(defonce tooltip-show (r/atom false))

(defn main-panel []
  (let [status @(re-frame/subscribe [::subs/status])]
    (case status
      :loading [:<>
                [main-header]
                [loading-modal]]
      :log-in [:<>
               [main-header]
               [log-in-modal]]
      :editing-user [:<>
                     [main-header]
                     [edit-user-modal]]
      :editing-game [:<> 
                     [main-header]
                     [edit-game-modal]]
      :main [:<>
             [main-header]
             [my-status]
             [lobbies]
             [games]
             [archived-games]
             [tooltip]
             [footer-version]])))

(defn main-header [] [:div.main-heading [:h1.mega "Mega"] [:h1.lan "LAN"]])
(defn my-status [] 
  (let [me @(re-frame/subscribe [::subs/current-user])
        id (:id me)
        free (= (:status me) "free")
        soon (= (:status me) "soon")
        busy (= (:status me) "busy")
        away (= (:status me) "away")
        now @status-poke
        ;; _ (js/console.log [now (:status-set me) (- now (:status-set me))])
        status-set-a-while-ago (= 0 (:status-set me))
        status-age-mins (js/Math.max (js/Math.floor (/ (- now (:status-set me)) (* 1000 60))) 0)
        refresh #(re-frame/dispatch [::evt/refresh-user-status])]
    [:<> [:div.my-status
          [:div.name
           [:span.dim (str "you are ")]
           [:span (str (:name me) " ")]
           [:a.link.dim
            {:on-click #(re-frame/dispatch [::evt/start-editing-user])}
            "(edit info)"]
           [:span " "]
           [:a.link.dim
            {:on-click #(re-frame/dispatch [::evt/log-out-as-user])}
            "(change user)"]]
          [:div.statuses
           [:a.action.free {:class    [(when free "active")]
                       :on-click #(re-frame/dispatch [::evt/update-player-status "free"])}
            [:span.name "free"] [:br] [:span.desc "I'm available for games"]]
           [:a.action.soon {:class [(when soon "active")]
                       :on-click #(re-frame/dispatch [::evt/update-player-status "soon"])}
            [:span.name "soon"] [:br] [:span.desc "I'll be available soon"]]
           [:a.action.busy {:class [(when busy "active")]
                       :on-click #(re-frame/dispatch [::evt/update-player-status "busy"])}
            [:span.name "busy"] [:br] [:span.desc "Currently playing something"]]
           [:a.action.away {:class [(when away "active")]
                       :on-click #(re-frame/dispatch [::evt/update-player-status "away"])}
            [:span.name "away"] [:br] [:span.desc "Not doing MegaLAN"]]]
          [:div.status-age.dim
           [:span (if status-set-a-while-ago "status set more than a day ago " (str "status set " status-age-mins " minute" (when (not= 1 status-age-mins) "s") " ago "))]
           [:a.link.dim {:on-click refresh}
            "(refresh now)"]]]
            [:div.player-preview {:style {:float "right" :margin-top "3.5rem"}} [player me]]]))

(defn lobbies []
  (let [ls @(re-frame/subscribe [::subs/lobbies])
        ls (sort-by (juxt (comp - :created-at) :name) ls)]
    [:div.lobbies
     [:div.heading
      [:h2 "Open game lobbies"]]
     [:div.body
      (when (= 0 (count ls)) [:p {:style {:margin-left "1rem"}} "No game lobbies are currently running."])
      (for [l ls]
        ^{:key (:id l)} [lobby l])]]))

(defn lobby [l]
  (let [{:keys [id game notes players]} l
        all-players @(re-frame/subscribe [::subs/all-players])
        my-uuid @(re-frame/subscribe [::subs/current-user-id])
        players (filter #((set (map name (keys players))) (:id %)) all-players)
        players (sort-by p-sort-priority players)
        p-msg (if (empty? players) "no players" (clojure.string/join ", " (map :name players)))
        confirm-msg (str "Do you want to delete the lobby for " game " containing " p-msg "?")
        kill-listener #(when-let [_ (js/confirm confirm-msg)]
                         (re-frame/dispatch [::evt/delete-lobby id]))
        add-self-listener #(re-frame/dispatch [::evt/join-lobby id])
        rm-self-listener #(re-frame/dispatch [::evt/leave-lobby id])
        im-in-lobby ((set (map :id players)) my-uuid)
        a-notes-active (r/atom false)
        a-notes (r/atom notes)]
    [:div.lobby
     [:div.head
      [:h3 "Lobby: " game]
      [:i.fa-solid.fa-xmark.fa-2x.close-icon.point.link
       {:on-click kill-listener}]]
     [lobby-notes id notes]
     [:div.body
      (if (empty? players)
        [:p "(No players currently in lobby)"]
        [:div.players
         (map #(do ^{:key (:id %)}
                [:div.player {:class         [(:status %)]
                              :on-click      (fn [] (swap! tooltip-pinned not @tooltip-pinned))
                              :on-mouse-over (fn [] (do (reset! tooltip-hover true) (reset! tooltip-show %)))
                              :on-mouse-out  (fn [] (reset! tooltip-hover false))}
                 [:img.avatar {:src (str "https://www.gravatar.com/avatar/" (.md5 js/window (:gravatar-email %)))}]
                 [:span.name {:key (:id %)} (:name %)]]) players)])
      (if im-in-lobby
        [:button {:on-click rm-self-listener} "Remove self from lobby"]
        [:button {:on-click add-self-listener} "Add self to lobby"])]]))

(defn lobby-notes []
  (let [a-notes-active (r/atom false)
        a-notes (r/atom "")]
    (fn [id notes]
      [:div.mid
       (if @a-notes-active
         [:<>
          [re-com/input-textarea
           :placeholder "Write lobby notes - e.g. 'we're in discord voice channel megalan-amethyst'"
           :rows        10
           :model       a-notes
           :on-change   #(reset! a-notes %)]
          [:button {:on-click #(do (re-frame/dispatch [::evt/update-lobby-notes id @a-notes])
                                   (reset! a-notes-active false))}
           "Save"]]
         [:<>
          [:span (if (empty? notes) "(No notes entered)" [:> ReactMarkdown {:source notes}])]
          [:i.fa-regular.fa-pen-to-square.edit-icon.point.link {:on-click
                                                      #(do (reset! a-notes notes)
                                                           (reset! a-notes-active true))}]])])))

(defn in?
  "true if coll contains elm"
  [coll elm]
  (some #(= elm %) coll))

(defn games []
  (let [gs (re-frame/subscribe [::subs/games])
        filter-games (re-frame/subscribe [::subs/filter-games])
        sort-games-by (re-frame/subscribe [::subs/sort-games-by])
        game-name-filter (r/atom "")
        my-uuid (re-frame/subscribe [::subs/current-user-id])]
    (fn []
      (let [gs (cond @filter-games (filter #(in? (map name (concat (keys (:hi-players %)) (keys (:players %)))) @my-uuid) @gs)
                     :else @gs)
            gs (if (empty? @game-name-filter)
                 gs
                 (filter #(string/includes? (string/lower-case (:name %)) (string/lower-case @game-name-filter)) gs))
            ;; _ (js/console.log [@game-name-filter gs])
            gs (sort-by (case @sort-games-by
                          :date (juxt (comp - :created-at) :name)
                          :name (juxt :name :created-at))
                        gs)]
        [:div.games
         [:div.heading
          [:h2 {:style {:min-width "12rem"}} "Game list"]
          [re-com/input-text
           :placeholder "Filter games by name..."
           :model game-name-filter
           :change-on-blur? false
           :on-change #(reset! game-name-filter %)
           :style {:background-color "transparent" :color "white"}
           :parts {:wrapper {:class "text-filter"}}]
          ;; [:span.ml-break]
          (if @filter-games
            [:a.action.link {:on-click #(re-frame/dispatch [::evt/filter-games false])}
             "see all games in the game list"]
            [:a.action.link {:on-click #(re-frame/dispatch [::evt/filter-games true])}
             "see only games I'm interested in"])
          (case @sort-games-by
            :date [:a.action.link {:on-click #(re-frame/dispatch [::evt/sort-games-by :name])}
                   "sort games by name (instead of most recently created first)"]
            :name [:a.action.link {:on-click #(re-frame/dispatch [::evt/sort-games-by :date])}
                   "sort games by recently created (instead of name)"])
          [:a.action.link {:on-click #(re-frame/dispatch [::evt/start-creating-game])}
           "create a new game"]]
         [:div.body
          (when (= 0 (count gs)) [:p {:style {:margin-left "1rem"}} "No games are currently listed."])
          (for [g gs]
            ^{:key (:id g)} [game g])]]))))

(defn player [p]
  (when p
    (let [not-seen-recently (> (- (.now js/Date) (:status-set p)) (* 8 60 60 1000))]
      [:p.player
       {:key           (:id p)
        :class         [(:status p)]
        :on-click      (fn [] (swap! tooltip-pinned not @tooltip-pinned))
        :on-mouse-over (fn [] (reset! tooltip-hover true) (reset! tooltip-show p))
        :on-mouse-out  (fn [] (reset! tooltip-hover false))}
       [:img.avatar {:src (str "https://www.gravatar.com/avatar/" (.md5 js/window (:gravatar-email p)))
                     :class (when not-seen-recently "seen-ages-ago")}]])))

(defn game [g]
  (fn [g]
    (let [{:keys [id name notes hi-players players]} g
          all-players @(re-frame/subscribe [::subs/all-players])
          my-uuid @(re-frame/subscribe [::subs/current-user-id])
          hi-players (filter #((set (map core/name (keys hi-players))) (:id %)) all-players)
          hi-players (sort-by p-sort-priority hi-players)
          players (filter #((set (map core/name (keys players))) (:id %)) all-players)
          players (sort-by p-sort-priority players)
          listener #(when-let
                     [_ (js/confirm
                         (str "Do you want to create a lobby for " name "?"))]
                      (re-frame/dispatch [::evt/create-lobby name]))
          im-high-priority (seq (filter #(= (:id %) my-uuid) hi-players))
          im-potential-plyr (seq (filter #(= (:id %) my-uuid) players))
          add-high-listener #(re-frame/dispatch [::evt/update-game-playing-status id :high-pri])
          add-plyr-listener #(re-frame/dispatch [::evt/update-game-playing-status id :norm-pri])
          rm-self-listener #(re-frame/dispatch [::evt/update-game-playing-status id :removed])
          ;; player #(do [:img.avatar {:src (str "https://www.gravatar.com/avatar/" (.md5 js/window (:gravatar-email %)))}])
          ]
      [:div.game
       [:div.head
        [:h3 name]
        [:i.fa-regular.fa-pen-to-square.edit-icon.point.link {:on-click #(re-frame/dispatch [::evt/start-editing-game id])}]]
       [:div.mid
        [:> ReactMarkdown {:source notes}]]
       [:div.body
        [:h4 (str "especially interested players" (when-not (empty? hi-players) (str " (" (count hi-players) ")")))]
        (if (empty? hi-players)
          [:p.dim "(no especially interested players yet)"]
          [:div.players
           (map player hi-players)])
        [:h4 (str "interested players" (when-not (empty? players) (str " (" (count players) ")")))]
        (if (empty? players)
          [:p.dim "(no interested players yet)"]
          [:div.players
           (map player players)])
        (cond
          im-high-priority [:<>
                            [:button {:on-click add-plyr-listener} "Switch yourself to 'interested'"]
                            [:button {:on-click rm-self-listener} "Remove yourself from player list"]]
          im-potential-plyr [:<>
                             [:button {:on-click add-high-listener} "Switch yourself to 'especially interested'"]
                             [:button {:on-click rm-self-listener} "Remove yourself from player list"]]
          :else [:<>
                 [:button {:on-click add-high-listener} "Add yourself as especially interested player"]
                 [:button {:on-click add-plyr-listener} "Add yourself as interested player"]])]
       [:div.foot
        [:button.create-lobby.point
         {:on-click listener}
         (str "Create lobby for " name)]]])))

(defn archived-games []
  (let [gs @(re-frame/subscribe [::subs/archived-games])
        filter-games @(re-frame/subscribe [::subs/filter-games])
        my-uuid @(re-frame/subscribe [::subs/current-user-id])
        gs (cond filter-games (filter #(in? (map name (concat (keys (:hi-players %)) (keys (:players %)))) my-uuid) gs)
                 :else gs)]
    [:div.games
     [:div.heading
      [:h2 "Archived games (games from previous years)"]]
     [:div.body
      (for [g gs]
        ^{:key (:id g)} [archived-game g])]]))

(defn archived-game [g]
  (fn [g]
    (let [{:keys [id name notes hi-players players]} g
          all-players @(re-frame/subscribe [::subs/all-players])
          my-uuid @(re-frame/subscribe [::subs/current-user-id])
          hi-players (filter #((set (map core/name (keys hi-players))) (:id %)) all-players)
          hi-players (sort-by p-sort-priority hi-players)
          copy-listener #(re-frame/dispatch [::evt/start-copying-game {:name name :notes notes}])
          players (filter #((set (map core/name (keys players))) (:id %)) all-players)
          players (sort-by p-sort-priority players)
          ;; player #(do [:img.avatar {:src (str "https://www.gravatar.com/avatar/" (.md5 js/window (:gravatar-email %)))}])
          ]
      [:div.archived.game
       [:div.head
        [:h3 name]]
       [:div.mid
        [:> ReactMarkdown {:source notes}]]
       [:div.body
        [:h4 (str "especially interested players" (when-not (empty? hi-players) (str " (" (count hi-players) ")")))]
        (if (empty? hi-players)
          [:p.dim "(no especially interested players yet)"]
          [:div.players
           (map player hi-players)])
        [:h4 (str "interested players" (when-not (empty? players) (str " (" (count players) ")")))]
        (if (empty? players)
          [:p.dim "(no interested players yet)"]
          [:div.players
           (map player players)])]
       [:div.foot
        [:button.create-lobby.point
         {:on-click copy-listener}
         (str "Copy '" name "' to current game list ")]]])))

(defn tooltip []
  (let [show-tooltip (or @tooltip-pinned @tooltip-hover)]
    (if show-tooltip
      (let [{:keys [name gravatar-email notes status status-set]} @tooltip-show
            full-status (condp = status
                          "free" "free (available to play)"
                          "soon" "soon (soon available to play)"
                          "busy" "busy (unavailable to play)"
                          "away" "away (unavailable to play)")
            now (.now js/Date)
            status-age-mins (js/Math.floor (/ (- now status-set) (* 1000 60)))
            status-set-a-while-ago (= 0 status-set)]
        [:div.ml-tooltip {:class [status]}
         (when gravatar-email
           [:img.avatar.big {:src (str "https://www.gravatar.com/avatar/" (.md5 js/window gravatar-email))}])
         [:div.contents
          [:h3 name]
          [:p.dim "status: " [:span.st full-status] 
           (if status-set-a-while-ago " since more than a day ago."
               (str " since " status-age-mins " min" (when (not= 1 status-age-mins) "s") " ago."))]
          [:div.notes [:> ReactMarkdown {:source notes}]]]])
      [:<>])))

(defn loading-modal []
  (re-com/modal-panel :child [:<> [:h1 "Reticulating splines"] [:h2 "(loading data, please wait)"]]
                      :parts {:child-container {:class "ml-modal"}}))

(defn log-in-modal []
  (let [chosen-user (r/atom nil)
        chosen-user-id (r/atom nil)
        all-players (re-frame/subscribe [::subs/all-players-dropdown])
        create-name (r/atom "")
        create-email (r/atom "")
        create-notes (r/atom "")]
    (fn []
      [re-com/modal-panel
       :child [:<>
               [:h1 "Log in or create user"]
               [:p "The MegaLAN site operates with the 'Google spreadsheet' permission model: everyone is allowed to do everything." [:br] "Please take care."]
               [:p "Select your name from the drop-down list below, or create a new user."]
               [:h2 "Select existing user"]
               (if (nil? @all-players) [:b "Loading players..."]
                   [re-com/single-dropdown
                    :choices @all-players
                    :model chosen-user-id
                    :on-change #(do (reset! chosen-user (->> @all-players
                                                             (filter (fn [p] (= % (:id p))))
                                                             first
                                                             :label))
                                    (reset! chosen-user-id %))
                    :placeholder "(Choose a user)"
                    :width "100%"
                    :filter-box? true
                    :render-fn (fn [u] [:div
                                        [:img {:style {:height "1.5rem" :margin-right "1rem"} :src (:gravatar u)}]
                                        [:span (:label u)]])])
               [:button {:on-click #(when (not (empty? @chosen-user)) (re-frame/dispatch [::evt/log-in-as-user @chosen-user-id]))}
                (str (if (empty? @chosen-user) "(Choose a user above to continue)" (str "Log in as " @chosen-user)))]
               [:h2 "Create user"]
               [:div.row
                [:img.demo-av {:src (str "https://www.gravatar.com/avatar/" (when (not (empty? @create-email)) (.md5 js/window @create-email)))}]
                [:div.col
                 [re-com/input-text
                  :placeholder "Your name, as it will be displayed to all users"
                  :width       "100%"
                  :model       create-name
                  :on-change   #(reset! create-name %)
                  :style {:width "100%" :box-sizing "border-box"}]
                 [re-com/input-text
                  :placeholder "Your email, for Gravatar to supply you with an avatar"
                  :width       "100%"
                  :model       create-email
                  :on-change   #(reset! create-email %)
                  :style {:width "100%" :box-sizing "border-box"}]
                 [re-com/input-textarea
                  :placeholder "Notes for you, e.g. your discord username, battle.net, steam, switch friend codes, etc. (supports markdown)"
                  :width       "100%"
                  :rows        10
                  :model       create-notes
                  :on-change   #(reset! create-notes %)
                  :style {:width "100%" :box-sizing "border-box"}]
                 [:button {:on-click #(re-frame/dispatch [::evt/create-and-log-in-as-user {:name @create-name
                                                                                           :email @create-email
                                                                                           :notes @create-notes}])}
                  "Create & log in as user"]]]]
       :style {:overflow "scroll"}
       :parts {:child-container {:class "ml-modal"}}])))

(defn edit-user-modal []
  (let [current-user @(re-frame/subscribe [::subs/current-user])
        editing-name (r/atom (:name current-user))
        editing-email (r/atom (:gravatar-email current-user))
        editing-notes (r/atom (:notes current-user))]
    (fn []
      [re-com/modal-panel
       :child [:<>
               [:h2 "Edit user"]
               [:div.row
                [:img.demo-av {:src (str "https://www.gravatar.com/avatar/" (when (not (empty? @editing-email)) (.md5 js/window @editing-email)))}]
                [:div.col
                 [re-com/input-text
                  :placeholder "Your name, as it will be displayed to all users"
                  :width       "25rem"
                  :model       editing-name
                  :on-change   #(reset! editing-name %)
                  :style {:width "100%" :box-sizing "border-box"}]
                 [re-com/input-text
                  :placeholder "Your email, for Gravatar to supply you with an avatar"
                  :width       "25rem"
                  :model       editing-email
                  :on-change   #(reset! editing-email %)
                  :style {:width "100%" :box-sizing "border-box"}]
                 [re-com/input-textarea
                  :placeholder "Notes for you, e.g. your discord username, battle.net, steam, switch friend codes, etc. (supports markdown)"
                  :width       "25rem"
                  :rows        10
                  :model       editing-notes
                  :on-change   #(reset! editing-notes %)
                  :style {:width "100%" :box-sizing "border-box"}]
                 [:button
                  {:on-click #(re-frame/dispatch [::evt/update-user-information {:name @editing-name
                                                                                 :email @editing-email
                                                                                 :notes @editing-notes}])}
                  "Save changes to user"]
                 [:button
                  {:on-click #(re-frame/dispatch [::evt/cancel-editing-user])}
                  "Cancel and discard changes"]]]]
       :style {:overflow "scroll"
               :min-width "20em"}
       :parts {:child-container {:class "ml-modal"}}])))

(defn edit-game-modal []
  (let [game @(re-frame/subscribe [::subs/game-under-edit])
        game-type (:type game)
        game-id (:id game)
        a-name (r/atom (:name game))
        a-notes (r/atom (:notes game))
        ]
    (fn []
      [re-com/modal-panel
       :child [:<>
               [:h1 (case game-type
                      nil "Create a game"
                      :edit (str "Edit game: " (:name game))
                      :copy (str "Copy game: " (:name game)))]
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
               [re-com/input-text
                :placeholder "Name of game"
                :model a-name
                :width       "100%"
                :on-change #(reset! a-name %)
                :style {:width "100%" :box-sizing "border-box"}]
      ;;  [re-com/input-text 
      ;;   :placeholder "If a PC game, name of sponsor certifying the game is Not A Virus"
      ;;   :model a-sponsor
      ;;   :on-change #(reset! a-sponsor (.. % -target -value))]
               [re-com/input-textarea
                :placeholder "Game notes (markdown supported)"
                :rows 10
                :model a-notes
                :width       "100%"
                :on-change #(reset! a-notes %)
                :style {:width "100%" :box-sizing "border-box"}]

               (if game-id
                 [:button {:on-click #(re-frame/dispatch [::evt/save-game {:id game-id :name @a-name :notes @a-notes}])}
                  "Save changes to this game"]
                 [:button {:on-click #(re-frame/dispatch [::evt/save-game {:id nil :name @a-name :notes @a-notes}])}
                  "Create this game"])
               [:button {:on-click #(re-frame/dispatch [::evt/cancel-editing-game])}
                "Nope, cancel this (discard changes)"]]
       :style {:overflow "scroll"
               :min-width "20em"}
       :parts {:child-container {:class "ml-modal"}}])))
