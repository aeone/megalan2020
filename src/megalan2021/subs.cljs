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
 ::all-players
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
 ::archived-games
 (fn [db]
   (vals (get-in db [:fb :archived-games]))))

(re-frame/reg-sub
 ::lobbies
 (fn [db]
   (vals (get-in db [:fb :lobbies]))))

(re-frame/reg-sub
 ::filter-games
 (fn [db]
   (get-in db [:local :filter-games])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Level 3 
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(re-frame/reg-sub
 ::all-players-dropdown
 :<- [::all-players]
 (fn [all-players]
   (->> all-players
        (map #(hash-map
               :id (:id %)
               :label (:name %)
               :gravatar (str "https://www.gravatar.com/avatar/" (when (not (empty? (:gravatar-email %))) (.md5 js/window (:gravatar-email %)))))))))

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
