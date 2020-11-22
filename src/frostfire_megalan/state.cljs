(ns frostfire-megalan.state)

(defn lobby [game notes players]
  {:game game
   :notes notes
   :players players})

(defn game [name notes hi-players players]
  {:name name
   :notes notes
   :hi-players hi-players
   :players players})

(defn- letters [count]
       (-> "abcdefghijklmnopqrstuvwxyz"
           (clojure.string.upper-case)
           (seq)
           (shuffle)
           (->> (take count))))

(defn gen-lobby []
  (lobby
    (str "Game " (first (letters 1)))
    ""
    (map #(str "Player " %) (letters (rand-int 8)))))

(defn gen-game []
      (game
        (str "Game " (first (letters 1)))
        "Notes go here"
        (map #(str "Player " %) (letters (rand-int 4)))
        (map #(str "Player " %) (letters (rand-int 6)))))

(defn initial-state []
  {:lobbies (map gen-lobby (range 4))
   :games (map gen-game (range 15))})