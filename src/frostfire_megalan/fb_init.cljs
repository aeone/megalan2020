(ns frostfire-megalan.fb-init
  (:require
    ;["firebase/app" :default firebase]
    ;["firebase/database"]
    ))

(def database (atom nil))

;(defn firebase-init
;      []
;      (firebase/initializeApp
;        #js {:apiKey      "9AczbUFTjPDiXj5RqdJi9XJ48UM9FVcS8E35GPjw"
;             :authDomain  "megalan-dabm.firebaseapp.com"
;             :databaseURL "https://megalan-dabm.firebaseio.com"
;             :projectId   "megalan-dabm"})
;      (reset! database (firebase/database)))
