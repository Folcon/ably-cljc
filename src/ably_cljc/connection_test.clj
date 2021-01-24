(ns ably-cljc.connection-test
  (:require [ably-cljc.core :as ably]))


(defonce s (atom {}))


; 1) check for :matchmaking/want-game, if none, set self as :matchmaking/want-game
; 2) if some, rand pick one and :matchmaking/request-game, set self as :matchmaking/request-game
; 3) if receive :matchmaking/request-game, set self :matchmaking/playing-game and send :matchmaking/agree-game to the sender to play game
; 4) if receive :matchmaking/agree-game, gen-game and set self as :matchmaking/playing-game

(defn add-conn []
  (let [client-id (ably/make-client-id)
        client-opts (ably/get-wildcard-token-opts) #_(ably/get-api-opts client-id)
        client-state (atom {})
        ably (ably/connect client-opts)
        ably-channel (ably/get-channel ably "lobby")
        ably-listener (ably/make-channel-listener (fn [msg] (swap! client-state update :msg (fnil conj []) msg)))
        ably-presence (ably/make-presence-listener client-id)
        ably-completion (ably/make-completion-listener)]
    {:client-id client-id
     :ably-channel ably-channel
     :ably-listener ably-listener
     :ably-presence ably-presence
     :ably-completion ably-completion
     :client-state client-state}))

(defn ably-setup-subscriptions [{:keys [client-id ably-channel ably-listener ably-presence ably-completion] :as state}]
  (let [_ (ably/chan-subscribe ably-channel ably-listener)
        _ (ably/presence-subscribe ably-channel ably-presence)
        _ (ably/presence-enter ably-channel (str client-id) ably-completion)]
    (println :ably-setup-subscriptions client-id ably-completion)
    state))


(defn get-raw-presence-msgs [ably-channel]
  (-> ably-channel
    .-presence
    (.get #_true (ably/make-opts-param))))

(defn get-presence-msgs [chan]
  (ably/presence-messages-> (get-raw-presence-msgs chan)))

(defn get-self-presence-msg [client-id presence-msgs]
  (first (filter (fn [m] (= (str client-id) (:client-id m))) presence-msgs)))

(defn get-other-presence-msgs [client-id presence-msgs]
  (filterv (fn [m] (not= (str client-id) (:client-id m))) presence-msgs))

(defn get-opponents [client-id presence-msgs]
  (filterv (fn [m] (and (not= (str client-id) (:client-id m)) (= ":matchmaker/want-game" (:data m)))) presence-msgs))


;;; TODO: Random mutation testing
;; Model presence msgs as vector of messages

(comment
  ;; Setup
  (let [red-conn (add-conn)
        blu-conn (add-conn)]
    (swap! s assoc :red red-conn)
    (swap! s assoc :blu blu-conn))

  ;; join presence - red
  (let [conn-name :red
        conn (@s conn-name)]
    (ably-setup-subscriptions conn))

  ;; get-presence-msgs - red
  (let [conn-name :red
        chan (get-in @s [conn-name :ably-channel])]
    (ably/presence-messages-> (get-raw-presence-msgs chan)))

  ;; get-presence-msgs - blu
  (let [conn-name :blu
        chan (get-in @s [conn-name :ably-channel])]
    (ably/presence-messages-> (get-raw-presence-msgs chan)))

  ;; check if anyone is interested in a game, if not say that you want one if you haven't already
  (let [conn-name :red
        {:keys [client-id ably-channel] :as conn} (get-in @s [conn-name])
        presence-msgs (get-presence-msgs ably-channel)
        self (get-self-presence-msg client-id presence-msgs)
        opponents (get-opponents client-id presence-msgs)]
    (cond
      (seq opponents)
      (let [opponent (rand-nth opponents)
            {opp-client-id :client-id opp-id :id} opponent
            request-game-msg {:matchmaking/request-game {:from {:client-id client-id}
                                                         :to   {:id opp-id :client-id opp-client-id}}}]
        (println :playing-against opponent request-game-msg)
        #_#_
        (-> ably-channel .-presence (.updateClient (str client-id) ":matchmaker/request-game" (ably/make-completion-listener "request-game call-check-fn")))
        (.publish ably-channel ":matchmaking/request-game" (pr-str request-game-msg) (ably/make-completion-listener "request-game-publish call-check-fn")))

      (not= (:data self) ":matchmaker/want-game")
      (-> ably-channel .-presence (.updateClient (str client-id) ":matchmaker/want-game" (ably/make-completion-listener "want-game call-check-fn")))))

  ;; join presence - blu
  (let [conn-name :blu
        conn (@s conn-name)]
    (ably-setup-subscriptions conn))

  ;; get-opponents not the same as `get-other-presence-msgs`
  (let [conn-name :red
        {:keys [client-id ably-channel] :as conn} (@s conn-name)
        presence-msgs (get-presence-msgs ably-channel)]
    [(get-other-presence-msgs client-id presence-msgs)
     (get-opponents client-id presence-msgs)])

  ;; check if anyone is interested in a game, if not say that you want one if you haven't already
  (let [conn-name :blu
        {:keys [client-id ably-channel] :as conn} (get-in @s [conn-name])
        presence-msgs (get-presence-msgs ably-channel)
        self (get-self-presence-msg client-id presence-msgs)
        opponents (get-opponents client-id presence-msgs)]
    (cond
      (seq opponents)
      (let [opponent (rand-nth opponents)
            {opp-client-id :client-id opp-id :id} opponent
            request-game-msg {:matchmaking/request-game {:from {:client-id client-id}
                                                         :to   {:id opp-id :client-id opp-client-id}}}]
        (println :playing-against opponent request-game-msg)
        #_#_
        (-> ably-channel .-presence (.updateClient (str client-id) ":matchmaker/request-game" (ably/make-completion-listener "request-game call-check-fn")))
        (.publish ably-channel ":matchmaking/request-game" (pr-str request-game-msg) (ably/make-completion-listener "request-game-publish call-check-fn")))

      (not= (:data self) ":matchmaker/want-game")
      (-> ably-channel .-presence (.updateClient (str client-id) ":matchmaker/want-game" (ably/make-completion-listener "want-game call-check-fn"))))



    #_
    (let [opponents (filterv (fn [m] (not= (str client-id) (:client-id m))) presence-msgs)]
      (println :opponents opponents presence-msgs)
      (if (seq opponents)
        (let [opponent (rand-nth opponents)
              {opp-client-id :client-id opp-id :id} opponent
              request-game-msg {:matchmaking/request-game {:from {:client-id client-id}
                                                           :to   {:id opp-id :client-id opp-client-id}}}]
          (println :playing-against opponent request-game-msg)
          ;#_#_
          (-> ably-channel .-presence (.updateClient (str client-id) ":matchmaker/request-game" (ably/make-completion-listener "request-game call-check-fn")))
          (.publish ably-channel ":matchmaking/request-game" (pr-str request-game-msg) (ably/make-completion-listener "request-game-publish call-check-fn")))
        (-> ably-channel .-presence (.updateClient (str client-id) ":matchmaker/want-game" (ably/make-completion-listener "want-game call-check-fn")))))))


