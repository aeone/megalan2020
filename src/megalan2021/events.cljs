(ns megalan2021.events
  (:require
   [re-frame.core :as re-frame]
   [megalan2021.config :as config]
   [megalan2021.db :as db]
   [megalan2021.evt :as evt]
   [megalan2021.fb :as fb]
   [day8.re-frame.tracing :refer-macros [fn-traced]]))

(def ->local-store (re-frame/after db/settings->local-store))

(re-frame/reg-fx
 :firebase
 (fn [{:keys [key val]}]
   (fb/save! key val)))
(re-frame/reg-fx
 :firebase-n
 (fn [ds]
   (dorun (map #(fb/save! (:key %) (:val %)) ds))))

(re-frame/reg-event-fx
 ::evt/initialize-db
 [(re-frame/inject-cofx :local-settings)]
 (fn [{:keys [local-settings]} _]
   {:db (assoc db/default-db :local local-settings)}))

(re-frame/reg-event-db
 ::evt/fb-update-games
 (fn [db [_ data]] 
   (when config/debug? (println "Updating games"))
   (-> db
       (dissoc :loading)
       (assoc-in [:fb :games] data))))

(re-frame/reg-event-db
 ::evt/fb-update-archived-games-2020
 (fn [db [_ data]]
   (when config/debug? (println "Updating archived games 2020"))
   (let [_ (.log js/console ["Updating archived games 2020", data])]
   (-> db
       (dissoc :loading)
       (assoc-in [:fb :archived-games-2020] data)))))

(re-frame/reg-event-db
 ::evt/fb-update-archived-games-2021
 (fn [db [_ data]]
   (when config/debug? (println "Updating archived games 2021"))
   (let [_ (.log js/console ["Updating archived games 2021", data])]
   (-> db
       (dissoc :loading)
       (assoc-in [:fb :archived-games-2021] data)))))

(re-frame/reg-event-db
 ::evt/fb-update-archived-games-2022
 (fn [db [_ data]]
   (when config/debug? (println "Updating archived games 2022"))
   (let [_ (.log js/console ["Updating archived games 2022", data])]
   (-> db
       (dissoc :loading)
       (assoc-in [:fb :archived-games-2022] data)))))

(re-frame/reg-event-db
 ::evt/fb-update-players
 (fn [db [_ data]] 
   (when config/debug? (println "Updating players"))
   (-> db
       (dissoc :loading)
       (assoc-in [:fb :players] data))))

(re-frame/reg-event-db
 ::evt/fb-update-lobbies
 (fn [db [_ data]] 
   (when config/debug? (println "Updating lobbies"))
   (-> db
       (dissoc :loading)
       (assoc-in [:fb :lobbies] data))))

(re-frame/reg-event-fx
 ::evt/log-in-as-user
 [->local-store]
 (fn [{:keys [db]} [_ user-id]]
   {:db (assoc-in db [:local :current-user-id] user-id)}))

(re-frame/reg-event-fx
 ::evt/create-and-log-in-as-user
 [->local-store]
 (fn [{:keys [db]} [_ {:keys [name email notes]}]]
   (let [id (str (random-uuid))]
     {:db (assoc-in db [:local :current-user-id] id)
      :firebase-n [{:key [:players id :id]
                    :val id}
                   {:key [:players id :name]
                    :val name}
                   {:key [:players id :gravatar-email]
                    :val email}
                   {:key [:players id :notes]
                    :val notes}
                   {:key [:players id :status]
                    :val "away"}
                   {:key [:players id :status-set]
                    :val (.now js/Date)}]})))

(re-frame/reg-event-fx
 ::evt/start-editing-user
 [->local-store]
 (fn [{:keys [db]}]
   {:db (assoc-in db [:local :editing] :user)}))

(re-frame/reg-event-fx
 ::evt/cancel-editing-user
 [->local-store]
 (fn [{:keys [db]}]
   {:db (update-in db [:local] dissoc :editing)}))

(re-frame/reg-event-fx
 ::evt/log-out-as-user
 [->local-store]
 (fn [{:keys [db]}]
   {:db (update-in db [:local] dissoc :current-user-id)}))

(re-frame/reg-event-fx
 ::evt/refresh-user-status
 (fn [{:keys [db]}] 
   {:firebase {:key [:players (get-in db [:local :current-user-id]) :status-set]
               :val (.now js/Date)}}))

(re-frame/reg-event-fx
 ::evt/update-user-information
 (fn [{:keys [db]} [_ {:keys [name email notes]}]]
   {:db (update-in db [:local] dissoc :editing)
    :firebase-n [{:key [:players (get-in db [:local :current-user-id]) :name]
                  :val name}
                 {:key [:players (get-in db [:local :current-user-id]) :gravatar-email]
                  :val email}
                 {:key [:players (get-in db [:local :current-user-id]) :notes]
                  :val notes}]}))

(re-frame/reg-event-fx
 ::evt/delete-lobby
 (fn [_ [_ lobby-id]]
   {:firebase {:key [:lobbies lobby-id]
               :val nil}}))

(re-frame/reg-event-fx
 ::evt/join-lobby
 (fn [{:keys [db]} [_ lobby-id]]
   (let [user-id (get-in db [:local :current-user-id])]
     {:firebase {:key [:lobbies lobby-id :players user-id]
                 :val true}})))

(re-frame/reg-event-fx
 ::evt/leave-lobby
 (fn [{:keys [db]} [_ lobby-id]]
   (let [user-id (get-in db [:local :current-user-id])]
     {:firebase {:key [:lobbies lobby-id :players user-id]
                 :val nil}})))

(re-frame/reg-event-fx
 ::evt/update-lobby-notes
 (fn [{:keys [db]} [_ lobby-id notes]]
   {:firebase {:key [:lobbies lobby-id :notes]
               :val notes}}))

(re-frame/reg-event-fx
 ::evt/filter-games
 [->local-store]
 (fn [{:keys [db]} [_ val]]
   {:db (assoc-in db [:local :filter-games] val)}))

(re-frame/reg-event-fx
 ::evt/sort-games-by
 [->local-store]
 (fn [{:keys [db]} [_ val]]
   {:db (assoc-in db [:local :sort-games-by] val)}))

(re-frame/reg-event-fx
 ::evt/start-creating-game
 [->local-store]
 (fn [{:keys [db]}]
   {:db (assoc-in db [:local :editing] :game)}))

(re-frame/reg-event-fx
 ::evt/start-editing-game
 [->local-store]
 (fn [{:keys [db]} [_ game-id]]
   {:db (-> db
            (assoc-in [:local :editing] :game)
            (assoc-in [:local :editing-game] {:type :edit :id game-id}))}))

(re-frame/reg-event-fx
 ::evt/create-lobby
 (fn [{:keys [db]} [_ game-name]]
   (let [id (str (random-uuid))
         created-at (.now js/Date)]
     {:firebase {:key [:lobbies id]
                 :val {:id id
                       :game game-name
                       :notes ""
                       :players {}
                       :created-at created-at}}})))

(re-frame/reg-event-fx
 ::evt/update-game-playing-status
 (fn [{:keys [db]} [_ game-id status]]
   (let [user-id (get-in db [:local :current-user-id])]
     (case status
       :high-pri {:firebase-n [{:key [:games game-id :hi-players user-id]
                                :val true}
                               {:key [:games game-id :players user-id]
                                :val nil}]}
       :norm-pri {:firebase-n [{:key [:games game-id :hi-players user-id]
                                :val nil}
                               {:key [:games game-id :players user-id]
                                :val true}]}
       :removed {:firebase-n [{:key [:games game-id :hi-players user-id]
                               :val nil}
                              {:key [:games game-id :players user-id]
                               :val nil}]}))))

(re-frame/reg-event-fx
 ::evt/save-game
 [->local-store]
 (fn [{:keys [db]} [_ {:keys [id name notes]}]]
   (let [new-game (not id)
         id (or id (str (random-uuid)))
         created-at (.now js/Date)]
     {:db (update-in db [:local] dissoc :editing :editing-game)
      :firebase-n (remove nil?
                          [(when new-game {:key [:games id :id]
                                           :val id})
                           {:key [:games id :name]
                            :val name}
                           {:key [:games id :notes]
                            :val notes}
                           {:key [:games id :hi-players]
                            :val {}}
                           {:key [:games id :players]
                            :val {}}
                           {:key [:games id :created-at]
                            :val created-at}])})))

(re-frame/reg-event-fx
 ::evt/cancel-editing-game
 [->local-store]
 (fn [{:keys [db]} [_ game-id]]
   {:db (update-in db [:local] dissoc :editing :editing-game)}))

(re-frame/reg-event-fx
 ::evt/start-copying-game
 [->local-store]
 (fn [{:keys [db]} [_ {:keys [name notes]}]]
   {:db (-> db
            (assoc-in [:local :editing] :game)
            (assoc-in [:local :editing-game] {:type :copy :name name :notes notes}))}))

(re-frame/reg-event-fx
 ::evt/update-player-status
 (fn [{:keys [db]} [_ status]]
   (let [user-id (get-in db [:local :current-user-id])]
     {:firebase-n [{:key [:players user-id :status]
                    :val status}
                   {:key [:players user-id :status-set]
                    :val (.now js/Date)}]})))
