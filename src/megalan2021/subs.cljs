(ns megalan2021.subs
  (:require
   [re-frame.core :as re-frame]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Level 2
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(re-frame/reg-sub
 ::name
 (fn [db]
   (:name db)))

(re-frame/reg-sub
 ::status
 (fn [db]
   (cond 
     (:loading db) :loading
     (nil? (get-in db [:local :current-user-id])) :log-in
     (= :user (get-in db [:local :editing])) :editing-user
     (= :game (get-in db [:local :editing])) :editing-game
     :else :main)))

(re-frame/reg-sub
 ::editing-game
 (fn [db]
   (get-in db [:local :editing-game])))

(re-frame/reg-sub
 ::all-players-raw
 (fn [db]
   (vals (get-in db [:fb :players]))))

(re-frame/reg-sub
 ::current-user-id
 (fn [db]
   (get-in db [:local :current-user-id])))

(re-frame/reg-sub
 ::games
 (fn [db]
   (vals (get-in db [:fb :games]))))

(re-frame/reg-sub
 ::archived-games-2023
 (fn [db]
   (vals (get-in db [:fb :archived-games-2023]))))

(re-frame/reg-sub
 ::archived-games-2022
 (fn [db]
   (vals (get-in db [:fb :archived-games-2022]))))

(re-frame/reg-sub
 ::archived-games-2021
 (fn [db]
   (vals (get-in db [:fb :archived-games-2021]))))

(re-frame/reg-sub
 ::archived-games-2020
 (fn [db]
   (vals (get-in db [:fb :archived-games-2020]))))

(re-frame/reg-sub
 ::lobbies
 (fn [db]
   (vals (get-in db [:fb :lobbies]))))

(re-frame/reg-sub
 ::filter-games
 (fn [db]
   (get-in db [:local :filter-games])))

(re-frame/reg-sub
 ::sort-games-by
 (fn [db]
   (or (get-in db [:local :sort-games-by])
       :date)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Level 3 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(re-frame/reg-sub
 ::archived-games
 :<- [::archived-games-2020]
 :<- [::archived-games-2021]
 :<- [::archived-games-2022]
 :<- [::archived-games-2023]
 (fn [[games2020 games2021 games2022 games2023]]
   (let [games2023-names (set (map :name games2023))
         games2022-names (set (map :name games2022))
         games2021-names (set (map :name games2021))

         games2022-nonoverlap (filter #(not (games2023-names (:name %))) games2022)
         games2021-nonoverlap (filter #(not (or (games2023-names (:name %)) 
                                                (games2022-names (:name %)))) games2021)
         games2020-nonoverlap (filter #(not (or (games2023-names (:name %)) 
                                                (games2022-names (:name %)) 
                                                (games2021-names (:name %)))) games2020)
         _ (.log js/console {:games2020  games2020
                             :games2021  games2021
                             :games2022  games2022
                             :games2023  games2023
                             :games2022-nonoverlap  games2022-nonoverlap
                             :games2021-nonoverlap  games2021-nonoverlap
                             :games2020-nonoverlap  games2020-nonoverlap
                             })]
     (->> (concat games2023 games2022-nonoverlap games2021-nonoverlap games2020-nonoverlap)
          (filter some?)
          (sort-by :name)))))

(re-frame/reg-sub
 ::all-players-dropdown
 :<- [::all-players-raw]
 (fn [all-players]
   (->> all-players
        (map #(hash-map
               :id (:id %)
               :label (:name %)
               :gravatar (str "https://www.gravatar.com/avatar/" (when (not (empty? (:gravatar-email %))) (.md5 js/window (:gravatar-email %)))))))))


(re-frame/reg-sub
 ::all-players
 :<- [::all-players-raw]
 (fn [all-players]
   (->> all-players
        (map #(let [player-last-online (:status-set %)
                    now (.now js/Date)
                    a-day (* 1000 60 60 24)
                    a-day-ago (- now a-day)
                    away-player (-> %
                                    (assoc :status "away")
                                    (assoc :status-set 0))]
                (if (< player-last-online a-day-ago)
                  away-player
                  %))))))

(re-frame/reg-sub
 ::current-user
 :<- [::current-user-id]
 :<- [::all-players]
 (fn [[current-user-id all-players]]
   (->> all-players
        (filter #(= current-user-id (:id %)))
        (first))))

(re-frame/reg-sub
 ::game-under-edit
 :<- [::editing-game]
 :<- [::games]
 (fn [[editing-game games]]
   (let [game editing-game
         game-type (:type game)
         game-id (:id game)
         game-name (:name game)
         game-notes (:notes game)
         game-is-edit (= game-type :edit)
         game-is-copy (= game-type :copy)]
     (cond (nil? game) nil
           game-is-edit (->> games
                             (filter #(= game-id (:id %)))
                             (first)
                             (#(assoc % :type :edit)))
           game-is-copy game))))
