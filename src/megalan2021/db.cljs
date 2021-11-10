(ns megalan2021.db
  (:require [cljs.reader :as reader]
            [re-frame.core :as re-frame]))

(def default-db
  {:name "MegaLAN"
   :loading true})

(def ls-key "megalan2021")

(defn settings->local-store
  "Puts our settings into browser local storage."
  [db]
  (.setItem js/localStorage ls-key (str (:local db))))

(re-frame/reg-cofx
 :local-settings
 (fn [cofx _]
   (assoc cofx :local-settings
          (into (sorted-map)
                (some->> (.getItem js/localStorage ls-key)
                         (reader/read-string))))))
