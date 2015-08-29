(ns twitter-oauth-demo.oauth
  (:require [oauth.client :as oauth]
            [org.httpkit.client :as httpkit])
  (:import twitter.oauth.OauthCredentials))

(def app-key "sARdtKkzPl68VuFZrT4SB6KFW")
(def app-key-secret "oBorJS36lZdGt3GlqM7vOQtzBQQ0KILuJN80RkEbXEwGO0z6ak")

(defn make-app-consumer []
  (oauth/make-consumer app-key
                       app-key-secret
                       "https://api.twitter.com/oauth/request_token"
                       "https://api.twitter.com/oauth/access_token"
                       "https://api.twitter.com/oauth/authenticate"
                       :hmac-sha1))

(defn make-oauth-creds [consumer user-access-token]
  (OauthCredentials.
   consumer
   (:oauth_token user-access-token)
   (:oauth_token_secret user-access-token)))

(defn valid-access-token? [consumer access-token]
  (let [creds (oauth/credentials consumer
                                 (:oauth_token access-token)
                                 (:oauth_token_secret access-token)
                                 :GET
                                 "https://api.twitter.com/1.1/account/verify_credentials.json")]
    (= 200 (:status @(httpkit/get "https://api.twitter.com/1.1/account/verify_credentials.json"
                                  {:query-params creds})))))
