(ns frostfire-megalan.state)

(defn lobby [game players]
  {:game game
   :players players})

(defn game [name]
  {:name name})

(defn- letters [count]
       (-> "abcdefghijklmnopqrstuvwxyz"
           (clojure.string.upper-case)
           (seq)
           (shuffle)
           (->> (take count))))

(defn gen-lobby []
  (lobby
    (str "Game " (first (letters 1)))
    (map #(str "Player " %) (letters (rand-int 8)))))

(defn gen-game []
      (game (str "Game " (first (letters 1)))))

(defn initial-state []
  {:lobbies (map gen-lobby (range 4))
   :games (map gen-game (range 15))})