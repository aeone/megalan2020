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
       :status         "away"
       :status-set     (.now js/Date)
       :notes          notes})

(defn lobby [id game notes players]
      {:id      id
       :game    game
       :notes   notes
       :players players})

(defn game [id name notes sponsor hi-players players]
      {:id         id
       :name       name
       :sponsor    sponsor
       :notes      notes
       :hi-players hi-players
       :players    players})

; updaters
(defn create-player [name gravatar-email notes]
      (player (str (random-uuid)) name gravatar-email notes))

(defn create-game [name sponsor notes]
      (game (str (random-uuid)) name notes sponsor [] []))

;(defn add-self-to-lobby-generator [player-uuid lobby-uuid]
;      (fn [state]
;          (let [lobbies (:lobbies state)
;                lobbies-im-in (filter #(contains? (keys (:players %)) player-uuid) lobbies)]
;               (js/console.log "lobbies!")
;               (js/console.log lobbies)
;               (js/console.log lobbies-im-in)
;               (as-> state s
;                     (reduce #(update-in %1 [:lobbies (:id %2) :players] dissoc player-uuid) s lobbies-im-in)
;                     (assoc-in s [:lobbies lobby-uuid :players player-uuid] true)))))

(defn in?
      "true if coll contains elm"
      [coll elm]
      (some #(= elm %) coll))

(defn add-self-to-lobby-update-gen [player-uuid lobby-uuid]
      (fn [state]
          (let [lobbies (vals (:lobbies state))
                lobbies-im-in (filter #(in? (keys (:players %)) player-uuid) lobbies)
                player-key (keyword player-uuid)
                lobby-key (keyword lobby-uuid)
                ]
               (js/console.log "adding self to lobby")
               (js/console.log [lobbies lobbies-im-in])
               (concat
                 [[[:lobbies lobby-key :players player-key] true]]
                 (map #([[:lobbies (:id %) :players player-key]]) lobbies-im-in)))))

; test data generator
;(defn- letters [count]
;       (-> "abcdefghijklmnopqrstuvwxyz"
;           (clojure.string.upper-case)
;           (seq)
;           (shuffle)
;           (->> (take count))))
;
;(defn- gen-player []
;       (player
;         (str (random-uuid))
;         (-> "Alice Bob Carla Dina Eva Fiona Gina Hannah Ilya Jo"
;             (clojure.string.split #"\s")
;             (shuffle)
;             (first)
;             (str " " (first (letters 1))))
;         "test@example.com"
;         "Some notes"
;         ))
;
;(defn- gen-lobby [players]
;       (lobby
;         (str (random-uuid))
;         (str "Game " (first (letters 1)))
;         ""
;         (reduce #(assoc-in %1 [(:id %2)] true)
;                 {}
;                 (take (rand-int 8) (shuffle players)))))
;
;(defn- gen-game [players]
;       (game
;         (str (random-uuid))
;         (str "Game " (first (letters 1)))
;         "Notes go here"
;         (reduce #(assoc-in %1 [(:id %2)] true)
;                 {}
;                 (take (rand-int 4) (shuffle players)))
;         (reduce #(assoc-in %1 [(:id %2)] true)
;                 {}
;                 (take (rand-int 6) (shuffle players)))))
;
;(defn initial-state []
;      (let [inject (fn [xs] (reduce #(assoc %1 (:id %2) %2) {} xs))
;            players (map gen-player (range 10))]
;           {:players (inject players)
;            :lobbies (inject (map #(gen-lobby players) (range 4)))
;            :games   (inject (map #(gen-game players) (range 15)))}))

; state & updates
(defonce state (r/atom {:pending-load true}))
(def internal-state (r/atom (-> (.-localStorage js/window)
                                (.getItem "state")
                                (->> (.parse js/JSON))
                                (js->clj)
                                (or {}))))
(ratom/run! (.setItem (.-localStorage js/window)
                      "state"
                      (.stringify js/JSON (clj->js @internal-state))))
;(ratom/run! (-> @internal-state
;                (clj->js)
;                #(.setItem (.-localStorage js/window) "state" %)))

(def state-mod-chan (chan))
(def state-update-chan (chan))

(go-loop []
         (let [[func] (<! state-mod-chan)
               updates (func @state)]
              (js/console.log "Received message on state-mod-chan, with " (count updates) " updates.")
              (doseq [u updates]
                     (put! state-update-chan u))
              (recur)))

(go-loop []
         (let [[path val :as msg] (<! state-update-chan)
               path-head (butlast path)
               path-tail (last path)]
              (js/console.log "Received message on state-update-chan:")
              (js/console.log msg)
              (if val
                (swap! state #(assoc-in % path val))
                (swap! state #(update-in % path-head dissoc path-tail)))
              (put! fb/to-fb msg)
              (recur)))

(go-loop []
         (let [val (<! fb/from-fb)]
              (js/console.log "Received message on from-fb:")
              (js/console.log val)
              (reset! state val)
              (recur)))
