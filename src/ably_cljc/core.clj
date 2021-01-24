(ns ably-cljc.core
  (:import [io.ably.lib.rest AblyRest Auth$TokenParams]
           [io.ably.lib.realtime AblyRealtime Channel Presence Presence$PresenceListener CompletionListener Channel$MessageListener]
           [io.ably.lib.types ClientOptions Message PresenceMessage PresenceMessage$Action Param]))

;; TODO: Add in ably api key as env var
(def ably-api-key "")

(def ably-state (atom {}))


(comment
  (identity @ably-state)
  (reset! ably-state {})

  (messages->
    (:msg
     @(:client-state
       (second
         (second
           (identity @ably-state))))))

  (.name (PresenceMessage$Action/findByValue 1)))

(defn uuidv4 []
  (java.util.UUID/randomUUID))

(def make-client-id uuidv4)

(def user-hash-alice (uuidv4))
(def user-hash-bob (uuidv4))

(defn get-wildcard-token-opts
  "a wildcard token is required if you wish to have multiple clients connecting on the same ably instance"
  []
  (let [rest (AblyRest. ^String ably-api-key)
        params (Auth$TokenParams.)
        _ (set! (.-clientId params) "*")
        client-options (ClientOptions.)
        _ (set! (.-tokenDetails client-options) (-> rest .auth (.requestToken params nil)))]
    client-options))

(defn get-api-opts
  "use this to setup ably tying a instance to a client"
  [username]
  (let [client-options (ClientOptions. ably-api-key)
        _ (set! (.-clientId client-options) (str username))]
    client-options))

(defn connect [client-opts]
  (AblyRealtime. client-opts))

(defn get-channel [ably name]
  (-> ably .-channels (.get name)))

(defn make-presence-listener
  ([] (make-presence-listener nil))
  ([client-id]
   (reify Presence$PresenceListener
     (onPresenceMessage [_this msg]
       (println "presence-listen" client-id msg (.-action msg) (.-data msg))))))

(comment
  (make-presence-listener))

(defn make-channel-listener
  ([] (make-channel-listener nil))
  ([f]
   (reify Channel$MessageListener
     (onMessage [_this msg]
       (println "chan-listen" msg (.-data msg))
       (when (fn? f)
         (f msg))))))

(comment
  (make-channel-listener))

(defn make-completion-listener
  ([] (make-completion-listener ""))
  ([prefix]
   (reify CompletionListener
     (onSuccess [this]
       (println prefix " completion-listener sent" this))
     (onError [_this error]
       (println prefix " completion-listener error" error)))))

(defn chan-subscribe
  "use (make-channel-listener) for listener"
  [chan listener]
  (.subscribe chan listener))

(defn chan-unsubscribe
  "use prior (make-channel-listener) for listener"
  [chan listener]
  (.unsubscribe chan listener))

(defn presence-subscribe
  "use (make-presence-listener) for listener"
  [chan listener]
  (-> chan .-presence (.subscribe listener)))

(defn presence-unsubscribe
  "use prior (make-presence-listener) for listener"
  [chan listener]
  (-> chan .-presence (.unsubscribe listener)))

(defn chan->presence [^Channel chan]
  (.-presence chan))

(defn presence-enter
  "use (make-completion-listener) for listener"
  [chan client-id listener]
  (println chan client-id listener)
  (-> chan .-presence (.enterClient client-id (str client-id " joined") listener)))

(defn presence-leave
  "use prior (make-completion-listener) for listener"
  [chan client-id listener]
  (-> chan .-presence (.leaveClient client-id #_listener)))


(defn make-opts-param []
  (make-array Param 0))

(defn messages-> [messages]
  (mapv
    (fn [m]
      {:id (.-id m)
       :client-id (.-clientId m)
       :connection-id (.-connectionId m)
       :data (.-data m)
       :extras (.-extras m)
       :encoding (.-encoding m)
       :timestamp (.-timestamp m)})
    messages))

(defn presence-messages-> [messages]
  (mapv
    (fn [m]
      {:id (.-id m)
       :client-id (.-clientId m)
       :connection-id (.-connectionId m)
       :action (keyword (.name (.-action m)))
       :data (.-data m)
       :encoding (.-encoding m)
       :timestamp (.-timestamp m)})
    messages))

(comment
  (let [ably-alice (connect user-hash-alice)
        ably-bob (connect user-hash-bob)

        alice-listen (make-channel-listener)
        bob-listen (make-channel-listener)
        alice-present (make-presence-listener)
        bob-present (make-presence-listener)
        alice-channel (-> ably-alice .-channels (.get "lobby"))
        bob-channel (-> ably-bob .-channels (.get "lobby"))]
    (-> alice-channel
      (.subscribe alice-listen))
    (-> bob-channel
      (.subscribe bob-listen))
    #_#_#_#_#_#_#_#_
    (.publish alice-channel "example" "Hi Bob")
    (.publish bob-channel "example" "Hi Alice")
    (println "Alice Presence")
    (-> alice-channel
      .presence
      (.subscribe alice-present))
    (-> bob-channel
      .presence
      (.subscribe bob-present))
    (def alice-presence
      (-> alice-channel
        .presence
        (.get (make-array Param 0))))
    (println "Bob Presence")
    (def bob-presence
      (-> bob-channel
        .presence
        (.get false #_(make-array Param 0)))))

  (.-id alice-presence)

  (.length alice-presence)
  (.length bob-presence)

  (let [ably-alice (connect user-hash-alice)
        ably-bob (connect user-hash-bob)
        ably ably-alice
        noop (make-completion-listener)
        channel (-> ably .-channels (.get "lobby"))]
    (-> channel
      .presence
      (.get (make-array Param 0))
      #_(.enter nil noop)
      ;(.enterClient "Alice" noop)
      #_(.enterClient "Bob")))

  (let [ably-alice (connect user-hash-alice)
        ably-bob (connect user-hash-bob)

        noop (make-completion-listener)
        alice-channel (-> ably-alice .-channels (.get "lobby"))
        bob-channel (-> ably-bob .-channels (.get "lobby"))]
    (.publish alice-channel "update" "{ \"ready\": \"for game alice\" }" noop)
    (.publish bob-channel "update" "{ \"ready\": \"for game bob\" }" noop))

  (let [noop (make-completion-listener)
        channel (-> ably .-channels (.get "lobby"))]
    (-> channel
      .presence
      (.history (make-array Param 1))
      ;(.enterClient "Alice" noop)
      #_(.enterClient "Bob"))))
