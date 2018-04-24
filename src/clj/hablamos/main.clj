(ns hablamos.main
  (:require [org.httpkit.server :as hk]
            [hablamos.handler :as h])
  (:gen-class))

(defn -main
  "I don't do a whole lot ... yet."
  [&]
  (hk/run-server h/app {:port (or (Integer. (System/getenv "PORT")) 8080)}))
