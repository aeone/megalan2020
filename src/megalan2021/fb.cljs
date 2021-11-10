(ns megalan2021.fb
  (:require 
   [clojure.string :as string]
   [re-frame.core :as re-frame]
   ["firebase/app" :default firebase]
   ["firebase/database"]
   [megalan2021.config :as config]
   [megalan2021.evt :as evt]))

(defonce firebase-db
  (let [app (.initializeApp firebase
             #js {:apiKey      "9AczbUFTjPDiXj5RqdJi9XJ48UM9FVcS8E35GPjw"
                  :authDomain  "megalan-dabm.firebaseapp.com"
                  :databaseURL "https://megalan-dabm.firebaseio.com"
                  :projectId   "megalan-dabm"})]
    (.database firebase)))

(defn db-ref
  [path]
  (.ref firebase-db (string/join "/" path)))

(defn save!
  [path value]
  (let [path (clj->js path)
        value (clj->js value)]
    (.set (db-ref path) value)))

(defn remove!
  [path]
  (let [path (clj->js path)]
    (.remove (db-ref path))))

(defn db-subscribe []
  (let [register (fn [path event]
                   (when config/debug? (println "Registering " path))
                   (.on (db-ref path) "value"
                        (fn [snapshot]
                          (when config/debug? (println "Got " snapshot))
                          (re-frame/dispatch [event
                                              (js->clj ^js (.val snapshot) :keywordize-keys true)]))))]
    (register ["games"] ::evt/fb-update-games)
    (register ["archived-games"] ::evt/fb-update-archived-games)
    (register ["players"] ::evt/fb-update-players)
    (register ["lobbies"] ::evt/fb-update-lobbies)))
