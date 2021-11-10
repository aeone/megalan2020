(ns megalan2021.core
  (:require
   [reagent.dom :as rdom]
   [re-frame.core :as re-frame]
   [megalan2021.events :as events]
   [megalan2021.views :as views]
   [megalan2021.config :as config]
   [megalan2021.evt :as evt]
   [megalan2021.fb :as fb]
   ))


(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/main-panel] root-el)))

(defn init []
  (re-frame/dispatch-sync [::evt/initialize-db])
  (dev-setup)
  (fb/db-subscribe)
  (mount-root))
