(ns frostfire-megalan.core
  "This namespace contains your application and is the entrypoint for 'yarn start'."
  (:require [reagent.core :as r]
            [frostfire-megalan.hello :refer [hello]]
            [frostfire-megalan.main :refer [container]]
            [frostfire-megalan.fb-init :as fb-init]
            [frostfire-megalan.state :refer [state internal-state]]))

(defn ^:dev/after-load render
      "Render the toplevel component for this app."
      []
      (r/render [container state internal-state] (.getElementById js/document "app")))

(defn ^:export main
      "Run application startup logic."
      []
      (fb-init/firebase-init)
      (render))
