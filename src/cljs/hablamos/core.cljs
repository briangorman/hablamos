(ns hablamos.core
  (:require [reagent.core :as reagent :refer [atom]]
            [chord.client :refer [ws-ch]]
            [cljs.core.async :as async])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(defonce app-state (atom {:text "Hello world!"
                          :active-panel :login
                          :user "test"}))

(defonce msg-list (atom []))
(defonce users (atom {}))
(defonce send-chan (async/chan))

;; Websocket Routines

(defn send-msg
  [msg]
  (async/put! send-chan msg))

(defn send-msgs
  [svr-chan]
  (go-loop []
    (when-let [msg (async/<! send-chan)]
      (async/>! svr-chan msg)
      (recur))))

(defn receive-msgs
  [svr-chan]
  (go-loop []
    (if-let [new-msg (:message (<! svr-chan))]
      (do
        (case (:m-type new-msg)
          :init-users (reset! users (:msg new-msg))
          :chat (swap! msg-list conj (dissoc new-msg :m-type))
          :new-user (swap! users merge (:msg new-msg))
          :user-left (swap! users dissoc (:msg new-msg)))
        (recur))
      (println "Websocket closed"))))

(defn setup-websockets! []
  (go
    (let [{:keys [ws-channel error]} (async/<! (ws-ch "ws://localhost:3449/ws"))]
      (if error
        (println "Something went wrong with the websocket")
        (do
          (send-msg {:m-type :new-user
                     :msg (:user @app-state)})
          (send-msgs ws-channel)
          (receive-msgs ws-channel))))))
;; View Code

(defn chat-input []
  (let [v (atom nil)]
    (fn []
      [:div {:class "text-input"}
       [:form
        {:on-submit (fn [x]
                      (.preventDefault x)
                      (when-let [msg @v] (send-msg {:msg msg
                                                    :user (:user @app-state)
                                                    :m-type :chat})))}
        [:div {:style {:display "flex"
                       :flex-direction "column"}}
         [:input {:type "text"
                  :value @v
                  :placeholder "Type a message to send to the chatroom"
                  :on-change #(reset! v (-> % .-target .-value))}]
         [:button {:type "submit"
                   :class "button-primary"} "Send"]]]])))

(defn chat-history []
  (fn []
    [:div {:class "history"}
     (for [m @msg-list]
       ^{:key (:id m)} [:p (str (:user m) ": " (:msg m))])]))

(defn login-view []
  (let [v (atom nil)]
    (fn []
      [:div {:class "login-container"}
       [:div {:class "login"}
        [:form
         {:on-submit (fn [x]
                       (.preventDefault x)
                       (swap! app-state assoc :user @v)
                       (swap! app-state assoc :active-panel :chat)
                       (setup-websockets!))}
         [:input {:type "text"
                  :value @v
                  :placeholder "Pick a username"
                  :on-change #(reset! v (-> % .-target .-value))}]
         [:br]
         [:button {:type "submit"
                   :class "button-primary"} "Start chatting"]]]])))

(defn sidebar []
  (fn []
    [:div {:class "sidebar"}
     [:h5 "Active Users:"]
     (into [:ul]
           (for [[k v] @users]
             ^{:key k} [:li v]))]))

(defn chat-view []
  [:div {:class "chat-container"}
   [chat-history]
   [chat-input]
   [:div {:class "header"}
    [:h3 "core.async chat room"]]
   [sidebar]])

(defn app-container
  []
  (case (:active-panel @app-state)
    :login [login-view]
    :chat [chat-view]))

(reagent/render-component [app-container]
                          (. js/document (getElementById "app")))

