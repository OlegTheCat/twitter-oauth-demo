(ns twitter-oauth-demo.state
  (:require [twitter-oauth-demo.utils :as utils]
            [clojure.core.match :refer [match]]))

(def app-state (atom {}))

(defn modifier-for-kind [kind]
  (match kind
         :read ["<" :prefix]
         :write ["!" :suffix]))

(defn new-name-for-kind [old-name kind]
  (symbol
   (match (modifier-for-kind kind)
          [s :prefix] (str s old-name)
          [s :suffix] (str old-name s))))

(defmacro defstateop [name global-state kind args & body]
  `(do
     (defn ~name ~args ~@body)
     (defn ~(new-name-for-kind name kind) ~(vec (rest args))
       ~(match kind
               :read `(~name @~global-state ~@(rest args))
               :write `(swap! ~global-state ~name ~@(rest args))))))

(defstateop add-new-user app-state :write
  [app-state user-id screen-name user-access-token session-id]
  (-> app-state
      (assoc user-id {:access-token user-access-token
                      :screen-name screen-name
                      :session-id session-id})
      (assoc-in [:current-sessions session-id] user-id)))

(defstateop invalidate-session app-state :write
  [app-state session-id]
  (update app-state :current-sessions dissoc session-id))

(defn gen-session-id []
  (utils/gen-uuid))

(defstateop get-user-id-by-session-id app-state :read
  [app-state session-id]
  (get-in app-state [:current-sessions session-id]))

(defstateop get-user-by-session-id app-state :read
  [app-state session-id]
  (when-let [user-id (get-user-id-by-session-id app-state session-id)]
    (get app-state user-id)))

(defstateop get-access-token-by-user-id app-state :read
  [app-state user-id]
  (get-in app-state [user-id :access-token]))

(defstateop damage-access-token app-state :write
  [app-state user-id]
  (-> app-state
      (assoc-in [user-id :access-token :oauth_token] "blah")
      (assoc-in [user-id :access-token :oauth_token_secret] "blah")))

(defn reset-state []
  (reset! app-state {}))










