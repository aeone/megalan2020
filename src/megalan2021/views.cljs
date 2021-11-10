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
         tooltip)

;; (defn title []
;;   (let [name (re-frame/subscribe [::subs/name])
;;         games @(re-frame/subscribe [::subs/games])]
;;     [re-com/title
;;      :src   (at)
;;      :label (str "Hello from " @name". Git version: " config/version ".")
;;      :level :level1]))

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
             [tooltip]])))

(defn main-header [] [:div.main-heading [:h1.mega "Mega"] [:h1.lan "LAN"]])
(defn my-status [] 
  (let [me @(re-frame/subscribe [::subs/current-user])
        id (:id me)
        free (= (:status me) "free")
        soon (= (:status me) "soon")
        busy (= (:status me) "busy")
        away (= (:status me) "away")
        now @status-poke
        status-age-mins (js/Math.floor (/ (- now (:status-set me)) (* 1000 60)))
        refresh #(re-frame/dispatch [::evt/refresh-user-status])]
    [:div.my-status
     [:div.name
      [:span.dim (str "you are ")]
      [:span (str (:name me) " ")]
      [:span.link.dim
       {:on-click #(re-frame/dispatch [::evt/start-editing-user])}
       "(edit info)"]
      [:span " "]
      [:span.link.dim
       {:on-click #(re-frame/dispatch [::evt/log-out-as-user])}
       "(change user)"]]
     [:div.statuses
      [:div.free {:class    [(when free "active")]
                  :on-click (comment #(do (refresh) (put! state-update-chan [[:players id "status"] "free"])))}
       [:span.name "free"] [:br] [:span.desc "I'm available for games"]]
      [:div.soon {:class [(when soon "active")]
                  :on-click (comment #(do (refresh) (put! state-update-chan [[:players id "status"] "soon"])))}
       [:span.name "soon"] [:br] [:span.desc "I'll be available soon"]]
      [:div.busy {:class [(when busy "active")]
                  :on-click (comment #(do (refresh) (put! state-update-chan [[:players id "status"] "busy"])))}
       [:span.name "busy"] [:br] [:span.desc "Currently playing something"]]
      [:div.away {:class [(when away "active")]
                  :on-click (comment #(do (refresh) (put! state-update-chan [[:players id "status"] "away"])))}
       [:span.name "away"] [:br] [:span.desc "Not doing MegaLAN"]]]
     [:div.status-age.dim
      [:span (str "status set " status-age-mins " minute" (when (not= 1 status-age-mins) "s") " ago ")]
      [:span.link {:on-click refresh}
       "(refresh now)"]]]))

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
        _ (js/console.log [id game notes players])
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
      [:i.fal.fa-times.fa-2x.close-icon.point.link
       {:on-click kill-listener}]]
     [lobby-notes id notes]
     [:div.body
      (if (empty? players)
        [:p "(No players in lobby)"]
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
          [:i.fal.fa-edit.fa-lg.edit-icon.point.link {:on-click
                                                      #(do (reset! a-notes notes)
                                                           (reset! a-notes-active true))}]])])))

(defn in?
  "true if coll contains elm"
  [coll elm]
  (some #(= elm %) coll))

(defn games []
  (let [gs @(re-frame/subscribe [::subs/games])
        filter-games @(re-frame/subscribe [::subs/filter-games])
        my-uuid @(re-frame/subscribe [::subs/current-user-id])
        gs (cond filter-games (filter #(in? (map name (concat (keys (:hi-players %)) (keys (:players %)))) my-uuid) gs)
                 :else gs)
        gs (sort-by (juxt (comp - :created-at) :name) gs)]
    [:div.games
     [:div.heading
      [:h2 "Game list"]
      (if filter-games
        [:span.link {:on-click #(re-frame/dispatch [::evt/filter-games false])}
         "see all games in the game list"]
        [:span.link {:on-click #(re-frame/dispatch [::evt/filter-games true])}
         "see only games I'm interested in"])
      [:span.link {:on-click #(re-frame/dispatch [::evt/start-creating-game])}
       "create a new game"]]
     [:div.body
      (for [g gs]
        ^{:key (:id g)} [game g])]]))

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
                      (js/console.log (str "Creating lobby for " name))
                      (re-frame/dispatch [::evt/create-lobby name]))
          im-high-priority (seq (filter #(= (:id %) my-uuid) hi-players))
          im-potential-plyr (seq (filter #(= (:id %) my-uuid) players))
          add-high-listener #(re-frame/dispatch [::evt/update-game-playing-status id :high-pri])
          add-plyr-listener #(re-frame/dispatch [::evt/update-game-playing-status id :norm-pri])
          rm-self-listener #(re-frame/dispatch [::evt/update-game-playing-status id :removed])
          player #(do [:img.avatar {:src (str "https://www.gravatar.com/avatar/" (.md5 js/window (:gravatar-email %)))}])]
      [:div.game
       [:div.head
        [:h3 name]
        [:i.fal.fa-edit.fa-lg.edit-icon.point.link {:on-click #(re-frame/dispatch [::evt/start-editing-game id])}]]
       [:div.mid
        [:> ReactMarkdown {:source notes}]]
       [:div.body
        [:h4 (str "high priority players" (when-not (empty? hi-players) (str " (" (count hi-players) ")")))]
        (if (empty? hi-players)
          [:p.dim "(no high priority players)"]
          [:div.players
           (map #(vector :p.player {:key           (:id %)
                                    :class         [(:status %)]
                                    :on-click      (fn [] (swap! tooltip-pinned not @tooltip-pinned))
                                    :on-mouse-over (fn [] (do (reset! tooltip-hover true) (reset! tooltip-show %)))
                                    :on-mouse-out  (fn [] (reset! tooltip-hover false))} (player %)) hi-players)])
        [:h4 (str "potential players" (when-not (empty? players) (str " (" (count players) ")")))]
        (if (empty? players)
          [:p.dim "(no potential players)"]
          [:div.players
           (map #(vector :p.player {:key           (:id %)
                                    :class         [(:status %)]
                                    :on-click      (fn [] (swap! tooltip-pinned not @tooltip-pinned))
                                    :on-mouse-over (fn [] (do (reset! tooltip-hover true) (reset! tooltip-show %)))
                                    :on-mouse-out  (fn [] (reset! tooltip-hover false))} (player %)) players)])
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
         (str "Create lobby for " name)]]])))

(defn archived-games []
  (let [gs @(re-frame/subscribe [::subs/archived-games])
        filter-games @(re-frame/subscribe [::subs/filter-games])
        my-uuid @(re-frame/subscribe [::subs/current-user-id])
        gs (cond filter-games (filter #(in? (map name (concat (keys (:hi-players %)) (keys (:players %)))) my-uuid) gs)
                 :else gs)
        gs (sort-by (juxt (comp - :created-at) :name) gs)]
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
          player #(do [:img.avatar {:src (str "https://www.gravatar.com/avatar/" (.md5 js/window (:gravatar-email %)))}])]
      [:div.game
       [:div.head
        [:h3 name]]
       [:div.mid
        [:> ReactMarkdown {:source notes}]]
       [:div.body
        [:h4 (str "high priority players" (when-not (empty? hi-players) (str " (" (count hi-players) ")")))]
        (if (empty? hi-players)
          [:p.dim "(no high priority players)"]
          [:div.players
           (map #(vector :p.player {:key           (:id %)
                                    :class         [(:status %)]
                                    :on-click      (fn [] (swap! tooltip-pinned not @tooltip-pinned))
                                    :on-mouse-over (fn [] (do (reset! tooltip-hover true) (reset! tooltip-show %)))
                                    :on-mouse-out  (fn [] (reset! tooltip-hover false))} (player %)) hi-players)])
        [:h4 (str "potential players" (when-not (empty? players) (str " (" (count players) ")")))]
        (if (empty? players)
          [:p.dim "(no potential players)"]
          [:div.players
           (map #(vector :p.player {:key           (:id %)
                                    :class         [(:status %)]
                                    :on-click      (fn [] (swap! tooltip-pinned not @tooltip-pinned))
                                    :on-mouse-over (fn [] (do (reset! tooltip-hover true) (reset! tooltip-show %)))
                                    :on-mouse-out  (fn [] (reset! tooltip-hover false))} (player %)) players)])]
       [:div.foot
        [:button.create-lobby.point
         {:on-click copy-listener}
         (str "Copy '" name "' to current game list ")]]])))

(defn tooltip []
  (let [show-tooltip (or @tooltip-pinned @tooltip-hover)]
    (if show-tooltip
      (let [{:keys [name gravatar-email notes status status-set]} @tooltip-show
            _ (println @tooltip-show)
            full-status (condp = status
                          "free" "free (available to play)"
                          "soon" "soon (soon available to play)"
                          "busy" "busy (unavailable to play)"
                          "away" "away (unavailable to play)")
            now (.now js/Date)
            status-age-mins (js/Math.floor (/ (- now status-set) (* 1000 60)))]
        [:div.ml-tooltip {:class [status]}
         (when gravatar-email
           [:img.avatar.big {:src (str "https://www.gravatar.com/avatar/" (.md5 js/window gravatar-email))}])
         [:div.contents
          [:h3 name]
          [:p.dim "status: " [:span.st full-status] (str " since " status-age-mins " min" (when (not= 1 status-age-mins) "s") " ago.")]
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
                  :on-change   #(reset! create-name %)]
                 [re-com/input-text
                  :placeholder "Your email, for Gravatar to supply you with an avatar"
                  :width       "100%"
                  :model       create-email
                  :on-change   #(reset! create-email %)]
                 [re-com/input-textarea
                  :placeholder "Notes for you, e.g. your discord username, battle.net, steam, switch friend codes, etc. (supports markdown)"
                  :width       "100%"
                  :rows        10
                  :model       create-notes
                  :on-change   #(reset! create-notes %)]
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
                  :on-change   #(reset! editing-name %)]
                 [re-com/input-text
                  :placeholder "Your email, for Gravatar to supply you with an avatar"
                  :width       "25rem"
                  :model       editing-email
                  :on-change   #(reset! editing-email %)]
                 [re-com/input-textarea
                  :placeholder "Notes for you, e.g. your discord username, battle.net, steam, switch friend codes, etc. (supports markdown)"
                  :width       "25rem"
                  :rows        10
                  :model       editing-notes
                  :on-change   #(reset! editing-notes %)]
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
        _ (js/console.log game)
        game-type (:type game)
        game-id (:id game)
        a-name (r/atom (:name game))
        a-notes (r/atom (:notes game))
        ;; a-sponsor (r/atom (:sponsor game))
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
                :on-change #(reset! a-name %)]
      ;;  [re-com/input-text 
      ;;   :placeholder "If a PC game, name of sponsor certifying the game is Not A Virus"
      ;;   :model a-sponsor
      ;;   :on-change #(reset! a-sponsor (.. % -target -value))]
               [re-com/input-textarea
                :placeholder "Game notes (markdown supported)"
                :rows 10
                :model a-notes
                :width       "100%"
                :on-change #(reset! a-notes %)]

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
