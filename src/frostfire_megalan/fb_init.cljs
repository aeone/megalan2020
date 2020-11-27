(ns frostfire-megalan.fb-init
  (:require
    [clojure.core.async :refer [<! >! put! go-loop chan]]
    ["firebase/app" :default firebase]
    ["firebase/database"]
    ))

; channels
(defonce to-fb (chan))
(defonce from-fb (chan))

; fb utils
(defn db-ref
      [path]
      (.ref (.database firebase) (clojure.string.join "/" path)))

(defn save!
      [path value]
      (.set (db-ref path) value))

(defn db-subscribe
      [path]
      (.on (db-ref path)
           "value"
           (fn [snapshot]
               (put! from-fb (js->clj (.val snapshot) :keywordize-keys true))
               ;(reset! state/counter (js->clj (.val snapshot) :keywordize-keys true))
               )))

(defn firebase-init
      []
      (.initializeApp firebase
        #js {:apiKey      "9AczbUFTjPDiXj5RqdJi9XJ48UM9FVcS8E35GPjw"
             :authDomain  "megalan-dabm.firebaseapp.com"
             :databaseURL "https://megalan-dabm.firebaseio.com"
             :projectId   "megalan-dabm"})
      (db-subscribe [])
      ;(reset! database (.database firebase))
      )

; state helpers

;(defonce database (atom nil))
;(defonce temp-state (atom nil))

(go-loop []
         (let [[path value] (<! to-fb)]
              (save! path value)
              (recur)))
