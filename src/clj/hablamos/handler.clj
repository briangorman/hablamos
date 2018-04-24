(ns hablamos.handler
  (:require
   [org.httpkit.server :as hk]
   [chord.http-kit :refer [with-channel]]
   [compojure.core :refer :all]
   [compojure.route :as route]
   [clojure.core.async :as async]
   [ring.util.response :as resp]
   [medley.core :refer [random-uuid]]))

; Use a transducer to append a unique id to each message
(defonce main-chan (async/chan 1 (map #(assoc % :id (random-uuid)))))

(defonce main-mult (async/mult main-chan))

(def users (atom {}))

(defn ws-handler
  [req]
  (with-channel req ws-ch
    (let [client-tap (async/chan)
          client-id (random-uuid)]
      (async/tap main-mult client-tap)
      (async/go-loop []
        (async/alt!
          client-tap ([message]
                      (if message
                        (do
                          (async/>! ws-ch message)
                          (recur))
                        (async/close! ws-ch)))
          ws-ch ([{:keys [message]}]
                 (if message
                   (let [{:keys [msg m-type]} message]
                     (if (= m-type :new-user)
                       (do
                         (swap! users assoc client-id msg)
                         (async/>! ws-ch  {:id (random-uuid)
                                           :msg @users
                                           :m-type :init-users})
                         (async/>! main-chan (assoc message :msg {client-id (:msg message)})))
                       (async/>! main-chan message))
                     (recur))
                   (do
                     (async/untap main-mult client-tap)
                     (async/>! main-chan {:m-type :user-left
                                          :msg client-id})
                     (swap! users dissoc client-id)))))))))

(defroutes app
  (GET "/ws" [] ws-handler)
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))
