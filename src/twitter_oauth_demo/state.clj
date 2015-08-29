(ns twitter-oauth-demo.state
  (:require [twitter-oauth-demo.utils :as utils]))

(def app-state (atom {}))

(defn add-new-user [app-state user-id screen-name user-access-token session-id]
  (-> app-state
      (assoc user-id {:access-token user-access-token
                      :screen-name screen-name
                      :session-id session-id})
      (assoc-in [:current-sessions session-id] user-id)))

(defn gen-session-id []
  (utils/gen-uuid))

(defn get-user-id-by-session-id [app-state session-id]
  (get-in app-state [:current-sessions session-id]))

(defn get-user-by-session-id [app-state session-id]
  (when-let [user-id (get-user-id-by-session-id app-state session-id)]
    (get app-state user-id)))

(defn get-access-token-by-user-id [app-state user-id]
  (get-in app-state [user-id :access-token]))

(defn damage-access-token [app-state user-id]
  (-> app-state
      (assoc-in [user-id :access-token :oauth_token] "blah")
      (assoc-in [user-id :access-token :oauth_token_secret] "blah")))

(defn reset-state []
  (reset! app-state {}))










