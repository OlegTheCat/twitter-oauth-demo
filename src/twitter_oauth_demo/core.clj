(ns twitter-oauth-demo.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]

            [ring.adapter.jetty :as jetty]
            [ring.util.response :refer [redirect]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]

            [hiccup.core :refer :all]
            [hiccup.form :refer :all]
            [hiccup.def :refer :all]
            [hiccup.page :refer :all]

            [oauth.client :as oauth]

            [twitter.api.restful :as restful]
            [twitter.callbacks.handlers :as handlers]

            [twitter-oauth-demo.state :as state]
            [twitter-oauth-demo.oauth :as twitter-demo.oauth])
  (:import
   (twitter.callbacks.protocols SyncSingleCallback)))

(defn default-callback []
  (SyncSingleCallback. handlers/response-return-everything
                       (fn [resp]
                         (let [formatted-resp (handlers/response-return-everything
                                               resp :to-json? false)]
                           (throw (ex-info "Errorneous response"
                                           formatted-resp))))
                       handlers/exception-rethrow))

(def consumer (twitter-demo.oauth/make-app-consumer))

(defn- construct-request-token-from-cookies [cookies]
  (let [token (cookies "oauth_token")
        token-secret (cookies "oauth_token_secret")]
    (when (and token token-secret)
      {:oauth_token (:value token)
       :oauth_token_secret (:value token-secret)})))

(def ^:dynamic *current-user-id* nil)

(defn session-guard [handler]
  (fn [req]
    ;; (println "In session guard")
    ;; (println "Current user id: " *current-user-id*)
    ;; (println)
    (if *current-user-id*
      (handler req)
      (redirect "/"))))

(defn wrap-current-user-id [handler]
  (fn [req]
    ;; (println "In wrap-current-user-id")
    ;; (println "Cookies: " (:cookies req))
    ;; (println)
    (let [{:strs [session-id]} (:cookies req)]
      (binding [*current-user-id*
                (and
                 session-id
                 (:value session-id)
                 (state/<get-user-id-by-session-id (:value session-id)))]
        (handler req)))))

(defn wrap-invalid-token-response [handler]
  (fn [req]
    (try
      (handler req)
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          ;; (println "Data: " data)
          ;; TODO: smth more precise here
          (if (>= (get-in data [:status :code]) 400)
            (redirect "/exec-login")
            (throw e)))))))

(defn redirect-to-menu-if-logged [handler]
  (fn [req]
    (if *current-user-id*
      (redirect "/tweet-menu")
      (handler req))))

(defroutes login-routes
  (GET "/" []
    ;; (println "At /")
    (html5
     [:body
      (form-to
       [:get "/exec-login"]
       (submit-button "Log in with Twitter"))]))
  (GET "/exec-login" []
    ;; (println "At /exec-login")
    (let [request-token (oauth/request-token consumer "http://localhost:4343/logged")]
      (merge (redirect
              (oauth/user-approval-uri
               consumer
               (:oauth_token request-token)))
             {:cookies request-token})))
  (GET "/logged" {params :params cookies :cookies}
    ;; (println "At /logged")
    ;; (println "params: " params)
    ;; (println "cookies: " cookies)
    (let [request-token (construct-request-token-from-cookies cookies)
          verifier (params "oauth_verifier")]
      (when (and request-token verifier)
        (let [access-token (oauth/access-token
                            consumer
                            request-token
                            verifier)
              session-id (state/gen-session-id)]
          ;; (println "access token: " access-token)
          (state/add-new-user!
           (:user_id access-token)
           (:screen_name access-token)
           access-token
           session-id)
          (merge (redirect "/tweet-menu")
                 {:cookies {"session-id" {:value session-id}}}))))))

(defroutes logout-routes
  (GET "/exec-logout" {cookies :cookies}
    ;; (println "Cookies: " cookies)
    (when-let [session-id (:value (cookies "session-id"))]
      (state/invalidate-session! session-id))
    (merge (redirect "/")
           {:cookies {"session-id" {:value "kill", :max-age 1}}})))

(defroutes protected-routes
  (GET "/tweet-menu" []
    ;; (println "At /tweet-menu")
    (html5
     [:body
      (form-to
       [:post "/do-tweet"]
       (text-field "tweet")
       (submit-button "Tweet this!"))
      [:a {:href (str "/exec-logout")} "Get out of here!"]]))
  (POST "/do-tweet" [tweet]
    ;; (println "Tweet is: " tweet)
    (let [creds (twitter-demo.oauth/make-oauth-creds
                 consumer
                 (state/<get-access-token-by-user-id *current-user-id*))]
      (restful/statuses-update :oauth-creds creds
                               :params {:status tweet}
                               :callbacks (default-callback))
      (redirect "/tweet-menu"))))

(defroutes app-routes
  login-routes
  logout-routes
  (-> protected-routes
      (wrap-routes session-guard))
  (route/not-found "<h1>Page not found</h1>"))

(def app (-> app-routes
             wrap-invalid-token-response
             wrap-current-user-id
             wrap-params
             wrap-cookies
             wrap-stacktrace))

(comment
  (def server (jetty/run-jetty #'app {:port 4343 :join? false})))
