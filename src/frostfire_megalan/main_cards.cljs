(ns frostfire-megalan.main-cards
  (:require [reagent.core :as r]
            [devcards.core :as dc :refer [defcard deftest]]
            [cljs.test :include-macros true :refer [is]]
            ["@testing-library/react" :refer [render cleanup fireEvent]]
            [frostfire-megalan.main :refer [
                                            lobby
                                            game]])
  (:require-macros [devcards.core :as dc
                    :refer [defcard defcard-rg]]))

; Useful docs: http://rigsomelight.com/devcards/#!/devdemos.reagent

(defn testing-container
      "The container that should be used to render testing-library react components.
      We want to provide our own container so that the rendered devcards aren't used."
      []
      (let [app-div (js/document.createElement "div")]
           (.setAttribute app-div "id" "testing-lib")
           (js/document.body.appendChild app-div)))

(defcard
  "This is a live interactive development environment using [Devcards](https://github.com/bhauman/devcards).")

(defcard-rg lobby-card-notes-players
            "### Lobby with notes and players"
            [lobby
             {:game    "Test Game"
              :notes   "I have some notes"
              :players ["Player A" "Player B" "Player C"]}])

(defcard-rg lobby-card-no-notes-no-players
            "### Lobby with no notes or players"
            [lobby
             {:game    "Test Game"
              :notes   ""
              :players []}])

(defcard-rg game-card-notes-players
            "### Game with notes and players"
            [game
             {:name "Super Smash Bros. Ultimate"
              :notes "
- Requires: Nintendo Switch & Nintendo Switch Online.
- Players: **2-8**
- Best with: 2, 4, 6.
- Platform fighting game with all the best characters in it."
              :hi-players ["Alice L"]
              :players ["Eva M"]}])


(defcard
  "You can also add tests here and see their results.
   Below are some tests using [React Testing Library](https://testing-library.com/docs/react-testing-library/intro).

   Tests will be ran outside the browser when you run the test command.")
