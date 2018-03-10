(ns hablamos.handler
  (:require
   [org.httpkit.server :as hk]
   [chord.http-kit :refer [with-channel]]
   [compojure.core :refer :all]
   [compojure.route :as route]
   [clojure.core.async :as a]
   [medley.core :refer [random-uuid]]))

; Use a transducer to append a unique id to each message
(defonce main-chan (a/chan 1 (map #(assoc % :id (random-uuid)))))

(defonce main-mult (a/mult main-chan))

(def users (atom {}))

(defn ws-handler
  [req]
  (with-channel req ws-ch
    (let [client-tap (a/chan)
          client-id (random-uuid)]
      (a/tap main-mult client-tap)
      (a/go-loop []
        (a/alt!
          client-tap ([message]
                      (if message
                        (do
                          (a/>! ws-ch message)
                          (recur))
                        (a/close! ws-ch)))
          ws-ch ([{:keys [message]}]
                 (if message
                   (let [{:keys [msg m-type]} message]
                     (do
                       (when (= m-type :new-user)
                         (swap! users assoc client-id msg)
                         (a/>! ws-ch  {:id (random-uuid)
                                       :msg (set (vals @users))
                                       :m-type :init-users}))
                       (a/>! main-chan message)
                       (recur)))
                   (do
                     (a/untap main-mult client-tap)
                     (a/>! main-chan {:m-type :user-left
                                      :msg (get @users client-id)})
                     (swap! users dissoc client-id)))))))))

(defroutes app
  (GET "/test" [] "<h1>Hello World</h1>")
  (GET "/ws" [] ws-handler)
  (route/not-found "<h1>Page not found</h1>"))
