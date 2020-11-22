(ns frostfire-megalan.state
  (:require [frostfire-megalan.fb-init :as fb]
    ;[firebase.app :as firebase-app]
    ;["firebase/app" :default firebase]
    ;["firebase/database" :as fbdb]
    ))

; models
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

; firebase
(defn initialise-firebase []
      (fb/firebase-init))

; test data generator
(defn- letters [count]
       (-> "abcdefghijklmnopqrstuvwxyz"
           (clojure.string.upper-case)
           (seq)
           (shuffle)
           (->> (take count))))
(defn- gen-lobby []
       (lobby
         (random-uuid)
         (str "Game " (first (letters 1)))
         ""
         (map #(str "Player " %) (letters (rand-int 8)))))

(defn- gen-game []
       (game
         (random-uuid)
         (str "Game " (first (letters 1)))
         "Notes go here"
         (map #(str "Player " %) (letters (rand-int 4)))
         (map #(str "Player " %) (letters (rand-int 6)))))

(defn initial-state []
      {:lobbies (map gen-lobby (range 4))
       :games   (map gen-game (range 15))})