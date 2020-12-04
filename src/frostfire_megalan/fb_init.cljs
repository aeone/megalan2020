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

(defn root-ref []
      (-> firebase
          (.database)
          (.ref)))

(defn save!
      [path value]
      (let [path (clj->js path)
            value (clj->js value)]
           (.set (db-ref path) value)))

(defn remove!
      [path]
      (let [path (clj->js path)]
           (.remove (db-ref path))))

;(defn save-at-root! [value]
;      (let [root-ref (.ref (.database firebase))
;            value (clj->js value)]
;           (js/console.log "Writing new value")
;           (js/console.log value)
;           (.set root-ref value)))

;(defn db-subscribe
;      [path]
;      (let [path (clj->js path)]
;           (.on (db-ref path)
;                "value"
;                (fn [snapshot]
;                    (put! from-fb (js->clj (.val snapshot) :keywordize-keys true))
;                    ;(reset! state/counter (js->clj (.val snapshot) :keywordize-keys true))
;                    ))))

(defn db-subscribe []
      (.on (root-ref)
           "value"
           (fn [snapshot]
               (put! from-fb (js->clj (.val snapshot) :keywordize-keys true)))))


(defn firebase-init
      []
      (.initializeApp firebase
        #js {:apiKey      "9AczbUFTjPDiXj5RqdJi9XJ48UM9FVcS8E35GPjw"
             :authDomain  "megalan-dabm.firebaseapp.com"
             :databaseURL "https://megalan-dabm.firebaseio.com"
             :projectId   "megalan-dabm"})
      (db-subscribe)
      ;(reset! database (.database firebase))
      )

; state helpers

;(defonce database (atom nil))
;(defonce temp-state (atom nil))

(go-loop []
         (let [[path value :as msg] (<! to-fb)]
              (js/console.log "Msg from to-fb channel:")
              (js/console.log msg)
              (if value
                (save! path value)
                (remove! path))
              (recur)))
