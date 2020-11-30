(ns frostfire-megalan.state
  (:require [clojure.core.async :refer [<! >! put! go-loop chan]]
            [reagent.core :as r]
            [reagent.ratom :as ratom]
            [frostfire-megalan.fb-init :as fb]))

; models
(defn player [id name gravatar-email notes]
      {:id             id
       :name           name
       :gravatar-email gravatar-email
       :notes          notes})

(defn lobby [id game notes players]
      {:id      id
       :game    game
       :notes   notes
       :players players})

(defn game [id name notes hi-players players]
      {:id         id
       :name       name
       :notes      notes
       :hi-players hi-players
       :players    players})

; test data generator
(defn- letters [count]
       (-> "abcdefghijklmnopqrstuvwxyz"
           (clojure.string.upper-case)
           (seq)
           (shuffle)
           (->> (take count))))

(defn- gen-player []
       (player
         (str (random-uuid))
         (-> "Alice Bob Carla Dina Eva Fiona Gina Hannah Ilya Jo"
             (clojure.string.split #"\s")
             (shuffle)
             (first)
             (str " " (first (letters 1))))
         "test@example.com"
         "Some notes"
         ))

(defn- gen-lobby [players]
       (lobby
         (str (random-uuid))
         (str "Game " (first (letters 1)))
         ""
         (take (rand-int 8) (shuffle players))))

(defn- gen-game [players]
       (game
         (str (random-uuid))
         (str "Game " (first (letters 1)))
         "Notes go here"
         (take (rand-int 4) (shuffle players))
         (take (rand-int 6) (shuffle players))))

(defn initial-state []
      (let [inject (fn [xs] (reduce #(assoc %1 (:id %2) %2) {} xs))
            players (map gen-player (range 10))]
           {:players (inject players)
            :lobbies (inject (map #(gen-lobby players) (range 4)))
            :games   (inject (map #(gen-game players) (range 15)))}))

; state & updates
(defonce state (r/atom (initial-state)))
(def internal-state (r/atom {}))

(def state-update-chan (chan))

(go-loop []
         (let [[path val :as msg] (<! state-update-chan)
               path-head (butlast path)
               path-tail (last path)]
              (js/console.log (str "suc1: Got message: " msg))
              (js/console.log (str "suc2: Swapping state - before: " @state))
              (if val
                (swap! state #(assoc-in % path val))
                (swap! state #(update-in % path-head dissoc path-tail)))
              (js/console.log (str "suc3: Swapping state - after: " @state))
              (put! fb/to-fb msg)))

(go-loop []
         (let [val (<! fb/from-fb)]
              (js/console.log val)
              ;(swap! state val)
              (recur)))

;(do (let [players (cursor state [:players])
;          lobbies (cursor state [:lobbies])
;          games (cursor state [:games])]
;         (ratom/run! (put! [["players"] players] fb/to-fb))
;         (ratom/run! (put! [["lobbies"] lobbies] fb/to-fb))
;         (ratom/run! (put! [["games"] games] fb/to-fb))
;         ))
;
;(ratom/run! (->> [@state]
;                 ;(update-in [:players]
;                ;           (fn [xs] (reduce #(assoc %1 (:id %2) %2) {} xs)))
;                (put! fb/to-fb)
;                ))

;(update-in
;  (initial-state)
;  [:players]
;  (fn [xs]
;      (reduce #(assoc %1 (:id %2) %2) {} xs)))

; experiments
;> (update-in {:test {:a 1 :b 2 :c 3} :other {:a 2 :b 3 :c 4}} [:test :other] (fn [x] (reduce #(assoc %1 (first %2) (* 10 (second %2))) {} x)))
;{:test {:a 1, :b 2, :c 3, :other {}}, :other {:a 2, :b 3, :c 4}}
